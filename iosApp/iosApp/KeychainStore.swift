import Foundation
import Security
import Shared

/// The iPhone Keychain for the login. Items are readable only on this device
/// (never in backups or on another phone) and only after the first unlock.
final class KeychainStore: SecureStore {
    private let service = "com.myclinic.app"

    func read(key: String) -> String? {
        var query = base(key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func write(key: String, value: String) {
        let data = Data(value.utf8)
        let status = SecItemUpdate(base(key) as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var add = base(key)
            add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    func delete(key: String) {
        SecItemDelete(base(key) as CFDictionary)
    }

    private func base(_ key: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: key]
    }
}
