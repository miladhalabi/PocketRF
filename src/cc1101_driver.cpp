#include "cc1101_driver.h"

CC1101Driver cc1101Driver;

CC1101Driver::CC1101Driver() {}

bool CC1101Driver::begin() {
    Serial.println("[CC1101] Initializing SPI interface...");
    Serial.printf(
        "[CC1101] SPI Pins: SCK=%d, MISO=%d, MOSI=%d, CS=%d, GDO0=%d\n",
        CC1101_SCK_PIN,
        CC1101_MISO_PIN,
        CC1101_MOSI_PIN,
        CC1101_CS_PIN,
        CC1101_GDO0_PIN
    );

    SPI.begin(CC1101_SCK_PIN, CC1101_MISO_PIN, CC1101_MOSI_PIN, CC1101_CS_PIN);
    ELECHOUSE_cc1101.setSPIinstance(&SPI);
    ELECHOUSE_cc1101.setSpiPin(CC1101_SCK_PIN, CC1101_MISO_PIN, CC1101_MOSI_PIN, CC1101_CS_PIN);
    ELECHOUSE_cc1101.setBeginEndLogic(false);
    ELECHOUSE_cc1101.setGDO0(CC1101_GDO0_PIN);
    ELECHOUSE_cc1101.Init();

    if (!ELECHOUSE_cc1101.getCC1101()) {
        Serial.println("[CC1101] ERROR: CC1101 module not detected on SPI bus!");
        m_initialized = false;
        return false;
    }

    m_initialized = true;
    Serial.printf(
        "[CC1101] Connected! Version: 0x%02X, PartNum: 0x%02X, MARCSTATE: 0x%02X\n",
        getVersion(),
        getPartNum(),
        getMarcState()
    );

    // Calibration table setup
    ELECHOUSE_cc1101.setClb(1, 24, 28); // 315 MHz band
    ELECHOUSE_cc1101.setClb(2, 31, 38); // 433 MHz band
    ELECHOUSE_cc1101.setClb(3, 65, 76); // 868 MHz band
    ELECHOUSE_cc1101.setClb(4, 77, 79); // 915 MHz band

    // ASK/OOK Modulation (2 = ASK/OOK)
    ELECHOUSE_cc1101.setModulation(2);

    // Asynchronous serial mode on GDO0 (3 = Asynchronous serial mode)
    ELECHOUSE_cc1101.setPktFormat(3);

    // Set default frequency
    setFrequency(DEFAULT_RF_FREQ, false);

    Serial.flush();
    return true;
}

bool CC1101Driver::isDetected() { return ELECHOUSE_cc1101.getCC1101(); }

uint8_t CC1101Driver::getMarcState() { return ELECHOUSE_cc1101.SpiReadStatus(CC1101_MARCSTATE) & 0x1F; }

uint8_t CC1101Driver::getVersion() { return ELECHOUSE_cc1101.SpiReadReg(CC1101_VERSION); }

uint8_t CC1101Driver::getPartNum() { return ELECHOUSE_cc1101.SpiReadReg(CC1101_PARTNUM); }

int CC1101Driver::getRssi() { return ELECHOUSE_cc1101.getRssi(); }

void CC1101Driver::waitForIdle() {
    const uint32_t start = millis();
    while ((ELECHOUSE_cc1101.SpiReadStatus(CC1101_MARCSTATE) & 0x1F) != 0x01) {
        if (millis() - start > 20) break;
        delay(1);
    }
}

uint8_t CC1101Driver::interpolateFsctrl0(
    float frequency, float minFreq, float maxFreq, uint8_t minValue, uint8_t maxValue
) {
    if (frequency <= minFreq) return minValue;
    if (frequency >= maxFreq) return maxValue;

    const float ratio = (frequency - minFreq) / (maxFreq - minFreq);
    return uint8_t(minValue + (ratio * float(maxValue - minValue)) + 0.5f);
}

void CC1101Driver::applyPreciseCalibration(float frequency, bool isTx) {
    uint8_t fsctrl0 = 0x00;
    uint8_t test0 = 0x09;
    bool highVco = true;

    if (frequency >= 280.0f && frequency <= 348.0f) {
        fsctrl0 = interpolateFsctrl0(frequency, 280.0f, 348.0f, 24, 28);
        highVco = frequency >= 322.88f;
    } else if (frequency >= 387.0f && frequency <= 464.0f) {
        fsctrl0 = interpolateFsctrl0(frequency, 387.0f, 464.0f, 31, 38);
        highVco = frequency >= 430.50f;
    } else if (frequency >= 779.0f && frequency <= 899.99f) {
        fsctrl0 = interpolateFsctrl0(frequency, 779.0f, 899.99f, 65, 76);
        highVco = frequency >= 861.0f;
    } else if (frequency >= 900.0f && frequency <= 928.0f) {
        fsctrl0 = interpolateFsctrl0(frequency, 900.0f, 928.0f, 77, 79);
        highVco = true;
    } else {
        return;
    }

    test0 = highVco ? 0x09 : 0x0B;

    ELECHOUSE_cc1101.SpiWriteReg(CC1101_FSCTRL0, fsctrl0);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_TEST0, test0);
    if (isTx) {
        ELECHOUSE_cc1101.SpiWriteReg(CC1101_FREND0, 0x11);
        ELECHOUSE_cc1101.setPA(12);
    } else {
        ELECHOUSE_cc1101.SpiWriteReg(CC1101_FREND1, 0xB6);
    }
    ELECHOUSE_cc1101.SpiStrobe(CC1101_SCAL);
    waitForIdle();

    if (highVco) {
        const uint8_t fscal2 = ELECHOUSE_cc1101.SpiReadReg(CC1101_FSCAL2);
        if (fscal2 < 0x20) {
            ELECHOUSE_cc1101.SpiWriteReg(CC1101_FSCAL2, fscal2 + 0x20);
            ELECHOUSE_cc1101.SpiStrobe(CC1101_SCAL);
            waitForIdle();
        }
    }
}

void CC1101Driver::applyFixedFreqOokPreset(bool isTx) {
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_FSCTRL1, 0x06);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_MDMCFG0, 0x00);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_MDMCFG1, 0x00);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_MDMCFG2, 0x30);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_MDMCFG3, 0x32);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_MDMCFG4, 0x67);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_MCSM0, 0x18);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_FOCCFG, 0x18);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_FREND0, 0x11);
    if (isTx) {
        ELECHOUSE_cc1101.SpiWriteReg(CC1101_FIFOTHR, 0x47);
        ELECHOUSE_cc1101.setPA(12);
    } else {
        ELECHOUSE_cc1101.SpiWriteReg(CC1101_FIFOTHR, 0x07);
        ELECHOUSE_cc1101.SpiWriteReg(CC1101_AGCCTRL0, 0x40);
        ELECHOUSE_cc1101.SpiWriteReg(CC1101_AGCCTRL1, 0x01);
        ELECHOUSE_cc1101.SpiWriteReg(CC1101_AGCCTRL2, 0xC7);
        ELECHOUSE_cc1101.SpiWriteReg(CC1101_FREND1, 0xB6);
    }
}

bool CC1101Driver::setFrequency(float frequency, bool isTx) {
    if (frequency < 280.0f || (frequency > 350.0f && frequency < 387.0f) ||
        (frequency > 468.0f && frequency < 779.0f) || frequency > 928.0f) {
        Serial.printf(
            "[CC1101] Invalid frequency %.2f MHz. Falling back to %.2f MHz\n", frequency, DEFAULT_RF_FREQ
        );
        frequency = DEFAULT_RF_FREQ;
    }

    m_currentFreq = frequency;
    setIdle();

    ELECHOUSE_cc1101.setMHZ(frequency);
    applyPreciseCalibration(frequency, isTx);
    applyFixedFreqOokPreset(isTx);

    Serial.printf("[CC1101] Frequency set to %.2f MHz (isTx: %s)\n", frequency, isTx ? "true" : "false");
    Serial.flush();
    return true;
}

bool CC1101Driver::setRxMode(float frequency) {
    if (!setFrequency(frequency, false)) return false;

    pinMode(CC1101_GDO0_PIN, INPUT);
    ELECHOUSE_cc1101.SetRx();
    Serial.printf("[CC1101] Switched to RX mode at %.2f MHz\n", frequency);
    Serial.flush();
    return true;
}

bool CC1101Driver::setTxMode(float frequency) {
    if (!setFrequency(frequency, true)) return false;

    ELECHOUSE_cc1101.setModulation(2);
    ELECHOUSE_cc1101.setPktFormat(3);
    ELECHOUSE_cc1101.SpiWriteReg(CC1101_IOCFG0, 0x0D);

    pinMode(CC1101_GDO0_PIN, OUTPUT);
    digitalWrite(CC1101_GDO0_PIN, LOW);
    ELECHOUSE_cc1101.setPA(12);
    ELECHOUSE_cc1101.SetTx();
    Serial.printf("[CC1101] Switched to TX mode at %.2f MHz (IOCFG0=0x0D, PA=12)\n", frequency);
    Serial.flush();
    return true;
}

void CC1101Driver::setIdle() {
    ELECHOUSE_cc1101.setSidle();
    Serial.println("[CC1101] Radio set to SIDLE");
    Serial.flush();
}
