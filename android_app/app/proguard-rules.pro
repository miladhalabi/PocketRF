# R8 rules for release builds.
#
# None needed yet: BLE notifications are parsed by hand with the platform's
# org.json, the signal library uses SQLiteOpenHelper directly, and nothing
# is loaded by reflection or a serialization library. Add keep rules here if
# that ever changes (e.g. adopting kotlinx.serialization or Gson).
