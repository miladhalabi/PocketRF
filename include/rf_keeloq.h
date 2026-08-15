#ifndef RF_KEELOQ_H
#define RF_KEELOQ_H

#include "rf_structs.h"
#include <Arduino.h>
#include <stdint.h>
#include <vector>

#define bitAt(x, n) (((x) >> (n)) & 1)
#define g5(x, a, b, c, d, e)                                                                                 \
    (bitAt(x, a) + bitAt(x, b) * 2 + bitAt(x, c) * 4 + bitAt(x, d) * 8 + bitAt(x, e) * 16)

#define KEELOQ_NLF 0x3A5C742E

#define KEELOQ_UNKNOWN_LEARNING 0
#define KEELOQ_SIMPLE_LEARNING 1
#define KEELOQ_NORMAL_LEARNING 2
#define KEELOQ_SECURE_LEARNING 3
#define KEELOQ_MAGIC_XOR_TYPE1_LEARNING 4
#define KEELOQ_MAGIC_SERIAL_TYPE1_LEARNING 6
#define KEELOQ_MAGIC_SERIAL_TYPE2_LEARNING 7
#define KEELOQ_MAGIC_SERIAL_TYPE3_LEARNING 8
#define KEELOQ_ERREKA_LEARNING 12
#define KEELOQ_PUJOL_LEARNING 13
#define KEELOQ_AERF_LEARNING 14

struct KeeloqKey {
    String name;
    uint64_t key;
    uint32_t type;
};

uint32_t keeloq_encrypt(const uint32_t data, const uint64_t key);
uint32_t keeloq_decrypt(const uint32_t data, const uint64_t key);
uint64_t keeloq_normal_learning(uint32_t data, const uint64_t key);
uint64_t keeloq_secure_learning(uint32_t data, uint32_t seed, const uint64_t key);
uint64_t keeloq_magic_xor_type1_learning(uint32_t data, uint64_t xorv);
uint64_t keeloq_derive_man(uint32_t type, uint32_t fix, uint32_t seed, uint64_t key);
uint32_t keeloq_build_hop(const String &mf_name, uint8_t btn, uint32_t serial, uint16_t cnt);
bool keeloq_identify(RfCodes &code);

#endif // RF_KEELOQ_H
