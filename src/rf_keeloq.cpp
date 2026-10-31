#include "rf_keeloq.h"
#include "rf_decoder.h"
#include <cstdlib>
#include <cstring>

uint32_t keeloq_encrypt(const uint32_t data, const uint64_t key) {
    uint32_t x = data;
    for (int r = 0; r < 528; r++) {
        uint8_t nlfInput = g5(x, 1, 9, 20, 26, 31);
        uint8_t nlfBit = bitAt(KEELOQ_NLF, nlfInput);
        uint8_t keyBit = bitAt(key, r & 63);
        uint8_t newBit = bitAt(x, 16) ^ bitAt(x, 0) ^ nlfBit ^ keyBit;
        x = (x >> 1) | ((uint32_t)newBit << 31);
    }
    return x;
}

uint32_t keeloq_decrypt(const uint32_t data, const uint64_t key) {
    uint32_t x = data;
    for (int r = 527; r >= 0; r--) {
        uint8_t nlfInput = g5(x, 0, 8, 19, 25, 30);
        uint8_t nlfBit = bitAt(KEELOQ_NLF, nlfInput);
        uint8_t keyBit = bitAt(key, r & 63);
        uint8_t newBit = bitAt(x, 31) ^ bitAt(x, 15) ^ nlfBit ^ keyBit;
        x = (x << 1) | newBit;
    }
    return x;
}

uint64_t keeloq_normal_learning(uint32_t data, const uint64_t key) {
    uint32_t low = keeloq_decrypt((data & 0x0FFFFFFF) | 0x20000000, key);
    uint32_t high = keeloq_decrypt((data & 0x0FFFFFFF) | 0x60000000, key);
    return ((uint64_t)high << 32) | low;
}

uint64_t keeloq_secure_learning(uint32_t data, uint32_t seed, const uint64_t key) {
    uint32_t low = keeloq_decrypt(seed, key);
    uint32_t high = keeloq_decrypt(data & 0x0FFFFFFF, key);
    return ((uint64_t)high << 32) | low;
}

uint64_t keeloq_magic_xor_type1_learning(uint32_t data, uint64_t xorv) {
    uint32_t ser = data & 0x0FFFFFFF;
    uint64_t derived = ((uint64_t)ser << 32) | ser;
    return derived ^ xorv;
}

uint64_t keeloq_derive_man(uint32_t type, uint32_t fix, uint32_t seed, uint64_t key) {
    switch (type) {
        case KEELOQ_NORMAL_LEARNING: return keeloq_normal_learning(fix, key);
        case KEELOQ_SECURE_LEARNING: return keeloq_secure_learning(fix, seed, key);
        case KEELOQ_MAGIC_XOR_TYPE1_LEARNING: return keeloq_magic_xor_type1_learning(fix, key);
        default: return key;
    }
}

// Per-manufacturer hop framing, ported 1-to-1 from Bruce's rf_keeloq.cpp
// keeloq_build_hop(). Most manufacturers use a plain button|serial|counter
// layout; a handful encode the serial differently (parity bits, fixed
// discriminators, derived nibbles) which this mirrors exactly.
uint32_t keeloq_build_hop(const String &mf_name, uint8_t btn, uint32_t serial, uint16_t cnt) {
    const uint32_t b = (uint32_t)btn << 28;

    if (mf_name == "Aprimatic") {
        uint32_t apri_serial = serial;
        uint8_t apr1 = 0;
        for (uint16_t i = 1; i != 0b10000000000; i <<= 1) {
            if (apri_serial & i) apr1++;
        }
        apri_serial &= 0b00001111111111;
        if (apr1 % 2 == 0) { apri_serial |= 0b110000000000; }
        return b | (apri_serial & 0xFFF) << 16 | cnt;
    } else if (
        mf_name == "DTM_Neo" || mf_name == "FAAC_RC,XT" || mf_name == "Mutanco_Mutancode" ||
        mf_name == "Came_Space" || mf_name == "Genius_Bravo" || mf_name == "GSN" || mf_name == "Rosh" ||
        mf_name == "Rossi" || mf_name == "Pecinin" || mf_name == "Peccinin" || mf_name == "Steelmate" ||
        mf_name == "Cardin_S449"
    ) {
        return b | (serial & 0xFFF) << 16 | cnt;
    } else if (mf_name == "NICE_Smilo" || mf_name == "NICE_MHOUSE" || mf_name == "JCM_Tech") {
        return b | (serial & 0xFF) << 16 | cnt;
    } else if (mf_name == "Merlin") {
        return b | (0x000) << 16 | cnt;
    } else if (mf_name == "Centurion") {
        return b | (0x1CE) << 16 | cnt;
    } else if (mf_name == "Monarch") {
        return b | (0x100) << 16 | cnt;
    } else if (mf_name == "Dea_Mio") {
        uint8_t first_disc_num = (serial >> 8) & 0xF;
        uint8_t result_disc = (0xC + (first_disc_num % 4));
        uint32_t dea_serial = (serial & 0xFF) | (((uint32_t)result_disc) << 8);
        return b | (dea_serial & 0xFFF) << 16 | cnt;
    }

    // Default route (Unknown and the plain manufacturers): 10-bit serial.
    return b | (serial & 0x3FF) << 16 | cnt;
}

uint64_t rf_reverse_bits(uint64_t num, uint8_t bits) {
    uint64_t res = 0;
    for (uint8_t i = 0; i < bits; ++i) {
        res <<= 1;
        res |= bitAt(num, i);
    }
    return res;
}

// --- Embedded keystore decryption (ported from Bruce's rf_keeloq.cpp) ------
#include "rf_keeloq_mfcodes_data.h"
#include <mbedtls/aes.h>

static std::vector<String> rf_split_string(String str, char c) {
    std::vector<String> cols{};
    size_t start = 0;
    while (start < str.length()) {
        int it = str.indexOf(c, start);
        if (it == -1) break;
        cols.emplace_back(&str[start], it - start);
        start = it + 1;
    }
    if (start <= str.length() && !str.isEmpty()) cols.emplace_back(&str[start], str.length() - start);
    return cols;
}

static void rf_parse_keystore(const String &content, std::vector<KeeloqKey> &keys) {
    int start = 0;
    const int len = content.length();
    while (start < len) {
        int nl = content.indexOf('\n', start);
        String line = (nl < 0) ? content.substring(start) : content.substring(start, nl);
        start = (nl < 0) ? len : nl + 1;
        line.trim();
        if (line.isEmpty()) continue;

        auto cols = rf_split_string(line, ';');
        if (cols.size() != 3) continue; // skip malformed lines, keep the rest
        keys.push_back({cols[0], std::strtoull(cols[1].c_str(), NULL, 16), (uint32_t)cols[2].toInt()});
    }
}

static String rf_keeloq_embedded_plaintext() {
    if (KEELOQ_MFCODES_ENC_LEN == 0 || (KEELOQ_MFCODES_ENC_LEN % 16) != 0) return "";

    std::vector<uint8_t> out(KEELOQ_MFCODES_ENC_LEN + 1, 0);
    mbedtls_aes_context ctx;
    mbedtls_aes_init(&ctx);
    if (mbedtls_aes_setkey_dec(&ctx, KEELOQ_MFCODES_KEY, 256) != 0) {
        mbedtls_aes_free(&ctx);
        return "";
    }

    uint8_t iv[16];
    memcpy(iv, KEELOQ_MFCODES_IV, sizeof(iv)); // CBC mutates the IV in place
    int rc = mbedtls_aes_crypt_cbc(
        &ctx, MBEDTLS_AES_DECRYPT, KEELOQ_MFCODES_ENC_LEN, iv, KEELOQ_MFCODES_ENC, out.data()
    );
    mbedtls_aes_free(&ctx);
    if (rc != 0) return "";

    size_t plen = KEELOQ_MFCODES_ENC_LEN;
    uint8_t pad = out[plen - 1];
    if (pad >= 1 && pad <= 16 && pad <= plen) plen -= pad;
    out[plen] = 0;

    return String((const char *)out.data());
}

KeeloqKeystore::KeeloqKeystore() {
    // Headless PocketRF has no SD/LittleFS keystore override: always use the
    // embedded, AES-encrypted copy (same blob Bruce ships).
    rf_parse_keystore(rf_keeloq_embedded_plaintext(), keys);
}

void keeloq_identify(RfCodes &code) {
    static KeeloqKeystore keystore; // decrypted once, reused for every capture
    uint32_t seed_eff = code.seed ? code.seed : (code.fix & 0x0FFFFFFF);

    for (const auto &key : keystore.get_keys()) {
        uint64_t man = keeloq_derive_man(key.type, code.fix, seed_eff, key.key);
        uint32_t decrypt = keeloq_decrypt(code.encrypted, man);

        if (key.mf_name == "Centurion") {
            if (code.keeloq_check_decrypt_centurion(decrypt)) {
                code.mf_name = key.mf_name;
                code.hop = decrypt;
                return;
            }
        }
        if (code.keeloq_check_decrypt(decrypt)) {
            code.mf_name = key.mf_name;
            code.hop = decrypt;
            return;
        }
    }
    // No keystore entry matched: leave mf_name as the caller's fallback
    // ("Generic KeeLoq").
}

bool rf_try_keeloq(const std::vector<int> &durations, RfCodes &out) {
    if (!rf_decode_keeloq(durations, out)) return false;

    // Real KeeLoq frames transmit each 32-bit half LSB-first, but our RMT
    // capture reconstructs `key` MSB-first in arrival order, so un-reverse it
    // before the fix/encrypted halves mean anything to the cipher.
    uint64_t yek = rf_reverse_bits(out.key, 64);
    out.fix = (uint32_t)(yek >> 32);
    out.btn = (uint8_t)(out.fix >> 28);
    out.encrypted = (uint32_t)(yek & 0xFFFFFFFF);
    out.serial = out.fix & 0x0FFFFFFF;
    out.seed = 0;
    out.mf_name = "Generic KeeLoq";

    keeloq_identify(out); // sets mf_name + cnt when a keystore entry matches
    return true;
}
