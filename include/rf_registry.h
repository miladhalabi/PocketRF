#ifndef RF_REGISTRY_H
#define RF_REGISTRY_H

#include "rf_protocol.h"
#include <Arduino.h>

const RfProtocolDef *rf_find_protocol(const String &name);
String rf_flipper_protocol_name(const String &canonical);
const RfProtocolDef *rf_protocol_for_number(int proto_no);
const RfProtocolDef *rf_protocol_at(int index);
int rf_protocol_count();

#endif // RF_REGISTRY_H
