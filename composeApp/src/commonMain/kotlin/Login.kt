import androidx.compose.runtime.MutableState
import data.DataHelper
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import misc.json
import misc.md5Hash
import platform.generateKey
import platform.httpClientPlatform
import platform.ownDecrypt
import platform.ownEncrypt
import platform.prefGet
import platform.prefRemove
import platform.prefSet
import top.yukonga.miuix.kmp.utils.Platform
import top.yukonga.miuix.kmp.utils.platform

private const val loginAuth2Url = "https://account.xiaomi.com/pass/serviceLoginAuth2"

fun isWeb(): Boolean = platform() == Platform.WasmJs || platform() == Platform.Js

/**
 * Login Xiaomi account.
 *
 * @param account: Xiaomi account
 * @param password: Password
 * @param global: Global or China account
 * @param savePassword: Save password or not
 * @param isLogin: Login status
 *
 * @return Login status
 */
suspend fun login(
    account: String,
    password: String,
    global: Boolean,
    savePassword: String,
    isLogin: MutableState<Int>
): Int {
    if (account.isEmpty() || password.isEmpty()) return 1
    if (savePassword != "1") deletePassword()

    val client = httpClientPlatform()
    val sid = if (global) "miuiota_intl" else "miuiromota"
    val md5Hash = md5Hash(password)

    try {
        client.get(loginAuth2Url)
        val response = client.post(loginAuth2Url) {
            parameter("_json", "true")
            parameter("user", account)
            parameter("hash", md5Hash)
            parameter("sid", sid)
        }

        val authStr = response.body<String>().replace("&&&START&&&", "")
        val authJson = json.decodeFromString<DataHelper.AuthorizeData>(authStr)
        val description = authJson.description
        val ssecurity = authJson.ssecurity
        val location = authJson.location
        val userId = authJson.userId.toString()
        val accountType = if (global) "GL" else "CN"
        val authResult = if (authJson.result == "ok") "1" else "0"

        if (description != "成功") return 3
        if (ssecurity == null || location == null || userId.isEmpty()) return 4

        if (savePassword == "1") {
            prefSet("savePassword", "1")
            savePassword(account, password)
        }

        val response2 = client.get(location) { parameter("_userIdNeedEncrypt", true) }
        val cookies = response2.headers["Set-Cookie"].toString().split("; ")[0].split("; ")[0]
        val serviceToken = cookies.split("serviceToken=")[1].split(";")[0]

        val loginInfo = DataHelper.LoginData(accountType, authResult, description, ssecurity, serviceToken, userId)
        prefSet("loginInfo", json.encodeToString(loginInfo))
        isLogin.value = 1
        return 0
    } catch (_: Exception) {
        return 2
    }
}

/**
 * Logout Xiaomi account.
 *
 * @param isLogin: Login status
 *
 * @return Logout status
 */
fun logout(isLogin: MutableState<Int>): Boolean {
    prefRemove("loginInfo")
    isLogin.value = 0
    return true
}

/**
 * Save Xiaomi's account & password.
 *
 * @param account: Xiaomi account
 * @param password: Password
 */
fun savePassword(account: String, password: String) {
    generateKey()
    val encryptedAccount = ownEncrypt(account)
    val encryptedPassword = ownEncrypt(password)
    prefSet("account", encryptedAccount.first)
    prefSet("accountIv", encryptedAccount.second)
    prefSet("password", encryptedPassword.first)
    prefSet("passwordIv", encryptedPassword.second)
}

/**
 * Delete Xiaomi's account & password.
 */
fun deletePassword() {
    prefRemove("account")
    prefRemove("accountIv")
    prefRemove("password")
    prefRemove("passwordIv")
}

/**
 * Get Xiaomi's account & password.
 *
 * @return Pair of Xiaomi's account & password
 */
fun getPassword(): Pair<String, String> {
    if (prefGet("account") != null && prefGet("password") != null && prefGet("accountIv") != null && prefGet("passwordIv") != null) {
        val encryptedAccount = prefGet("account").toString()
        val encodedAccountKey = prefGet("accountIv").toString()
        val encryptedPassword = prefGet("password").toString()
        val encodedPasswordKey = prefGet("passwordIv").toString()
        val account = ownDecrypt(encryptedAccount, encodedAccountKey)
        val password = ownDecrypt(encryptedPassword, encodedPasswordKey)
        return Pair(account, password)
    } else return Pair("", "")
}
