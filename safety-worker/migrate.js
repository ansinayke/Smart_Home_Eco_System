const path = require("path");
const admin = require("firebase-admin");
const { getDatabase } = require("firebase-admin/database");

const serviceAccount = require(path.join(__dirname, "serviceAccountKey.json"));

const app = admin.initializeApp({
  credential: admin.cert(serviceAccount),
  databaseURL: "https://smarthomesolutions-5d089-default-rtdb.asia-southeast1.firebasedatabase.app",
});

const db = getDatabase(app);

async function migrate() {
  console.log("Starting database migration...");
  const snapshot = await db.ref("house/floors").once("value");
  const floors = snapshot.val();
  
  if (!floors) {
    console.log("No floors found to migrate.");
    process.exit(0);
  }

  const updates = {};
  
  for (const [floorId, floor] of Object.entries(floors)) {
    const devices = floor.devices || {};
    for (const [deviceId, device] of Object.entries(devices)) {
      if (device.type === "OUTLET") {
        console.log(`Migrating OUTLET -> SMART_SWITCH: ${floorId}/${deviceId}`);
        updates[`house/floors/${floorId}/devices/${deviceId}/type`] = "SMART_SWITCH";
        updates[`house/floors/${floorId}/devices/${deviceId}/auto_cutoff_enabled`] = false;
        updates[`house/floors/${floorId}/devices/${deviceId}/max_on_duration_seconds`] = null; // optional removal
      } else if (device.type === "SAFETY_HAZARD") {
        console.log(`Migrating SAFETY_HAZARD -> SMART_SWITCH: ${floorId}/${deviceId}`);
        updates[`house/floors/${floorId}/devices/${deviceId}/type`] = "SMART_SWITCH";
        updates[`house/floors/${floorId}/devices/${deviceId}/auto_cutoff_enabled`] = true;
        // Keep max_on_duration_seconds intact
      }
    }
  }

  if (Object.keys(updates).length > 0) {
    await db.ref().update(updates);
    console.log("Migration complete!");
  } else {
    console.log("No devices required migration.");
  }
  
  process.exit(0);
}

migrate().catch(console.error);
