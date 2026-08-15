#include "rf_keeloq.h"

uint32_t keeloq_encrypt(const uint32_t data, const uint64_t key) {
    uint32_t x = data;
    for (int r = 0; r < 528; r++) {
        uint8_t nlfInput = g5(x, 31, 26, 20, 9, 1);
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
        uint8_t nlfInput = g5(x, 30, 25, 19, 8, 0);
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

uint32_t keeloq_build_hop(const String &mf_name, uint8_t btn, uint32_t serial, uint16_t cnt) {
    uint8_t disc = (uint8_t)(serial & 0xFF);
    return ((uint32_t)(btn & 0x0F) << 28) | ((uint32_t)disc << 16) | cnt;
}

bool keeloq_identify(RfCodes &code) {
    if (code.protocol != "KeeLoq" || code.Bit != 64) return false;

    uint32_t encrypted = (uint32_t)(code.key >> 32);
    uint32_t fix = (uint32_t)(code.key & 0xFFFFFFFF);

    code.encrypted = encrypted;
    code.fix = fix;
    code.serial = fix & 0x0FFFFFFF;
    code.btn = (uint8_t)(fix >> 28);

    // Default identification fallback
    code.mf_name = "Generic KeeLoq";
    return true;
}
