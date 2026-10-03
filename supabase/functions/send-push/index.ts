// =============================================================================
// send-push: delivers a phone notification for each new row in
// public.notifications (called by a Supabase Database Webhook on INSERT).
//
// What is sent: only the KIND of event ("consult_request", ...) and the
// notification id, as a data message. The app turns that into a translated,
// generic text ("New consult request") and loads the details itself after
// sign-in. No patient name or clinical text ever goes through Google's
// servers.
//
// Secrets (Supabase Dashboard -> Edge Functions -> Secrets):
//   FCM_SERVICE_ACCOUNT  the Firebase service-account JSON (see docs/SETUP.md)
// Provided automatically by Supabase: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY
// =============================================================================
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.4";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY, { auth: { persistSession: false } });

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

let cachedToken: { value: string; expiresAt: number } | null = null;

Deno.serve(async (req) => {
  // Only the database webhook (which sends the service-role key) may call this.
  const bearer = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!bearer || bearer !== SERVICE_ROLE_KEY) {
    return new Response("Forbidden", { status: 403 });
  }

  let notificationId: string | undefined;
  try {
    const payload = await req.json();
    notificationId = payload?.record?.id;
  } catch {
    return new Response("Bad request", { status: 400 });
  }
  if (!notificationId) return new Response("No notification", { status: 400 });

  // Re-read the row instead of trusting the request body.
  const { data: notification, error } = await admin
    .from("notifications")
    .select("id, recipient_id, kind")
    .eq("id", notificationId)
    .maybeSingle();
  if (error || !notification) return new Response("Not found", { status: 404 });

  const { data: devices } = await admin
    .from("device_tokens")
    .select("token")
    .eq("user_id", notification.recipient_id);
  if (!devices || devices.length === 0) return Response.json({ sent: 0 });

  const account: ServiceAccount = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT") ?? "{}");
  if (!account.private_key) return new Response("FCM_SERVICE_ACCOUNT secret is missing", { status: 500 });
  const accessToken = await googleAccessToken(account);

  let sent = 0;
  for (const { token } of devices) {
    const res = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          data: { kind: notification.kind, notification_id: notification.id },
          android: { priority: "HIGH" },
        },
      }),
    });
    if (res.ok) {
      sent++;
    } else {
      const text = await res.text();
      // The app was uninstalled or the token expired: forget this phone.
      if (res.status === 404 || text.includes("UNREGISTERED") || text.includes("INVALID_ARGUMENT")) {
        await admin.from("device_tokens").delete().eq("token", token);
      } else {
        console.error("FCM error", res.status); // never log the token or content
      }
    }
  }
  return Response.json({ sent });
});

/** OAuth access token for FCM, from the service account (signed JWT, cached ~55 min). */
async function googleAccessToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt > now + 60) return cachedToken.value;

  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const encode = (obj: unknown) => base64url(new TextEncoder().encode(JSON.stringify(obj)));
  const unsigned = `${encode(header)}.${encode(claims)}`;

  const pem = account.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)),
  );
  const jwt = `${unsigned}.${base64url(signature)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!res.ok) throw new Error(`Google token request failed: ${res.status}`);
  const body = await res.json();
  cachedToken = { value: body.access_token, expiresAt: now + (body.expires_in ?? 3600) };
  return cachedToken.value;
}

function base64url(bytes: Uint8Array): string {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
