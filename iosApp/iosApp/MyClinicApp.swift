import SwiftUI
import Shared

/// The iPhone app. Everything you see is the shared Kotlin app (MainViewController);
/// this file only starts it, passes email links to it, and covers it in the app switcher.
@main
struct MyClinicApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let url = Bundle.main.object(forInfoDictionaryKey: "SupabaseUrl") as? String ?? ""
        let key = Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String ?? ""
        MyClinicIos.shared.start(supabaseUrl: url, supabaseAnonKey: key, secureStore: KeychainStore())
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                ComposeView().ignoresSafeArea(.keyboard)
                // iOS takes a picture of the app for the app switcher: show a cover instead of patient data.
                if scenePhase != .active { PrivacyCover() }
            }
            .onOpenURL { url in MyClinicIos.shared.handleUrl(url: url.absoluteString) }
        }
    }
}

/// Hosts the shared Compose screens.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MyClinicIosKt.MainViewController() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// What the app switcher shows: the app name and a lock, never a record.
struct PrivacyCover: View {
    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            VStack(spacing: 12) {
                Image(systemName: "lock.fill").font(.system(size: 48)).foregroundColor(.accentColor)
                Text("My Clinic").font(.title2)
            }
        }
    }
}
