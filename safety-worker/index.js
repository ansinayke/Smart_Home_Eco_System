/**
 * Safety Worker — independent Firebase RTDB microservice.
 * Enforces max-on-duration cutoffs for SMART_SWITCH devices.
 * Event-driven: listens with .on('value'); uses one-shot timers (never setInterval).
 */

const path = require("path");
const admin = require("firebase-admin");
const { getDatabase } = require("firebase-admin/database");

const serviceAccount = require(path.join(__dirname, "serviceAccountKey.json"));

const app = admin.initializeApp({
  credential: admin.cert(serviceAccount),
  databaseURL:
    "https://smarthomesolutions-5d089-default-rtdb.asia-southeast1.firebasedatabase.app",
});

const db = getDatabase(app);

/** @type {Map<string, NodeJS.Timeout>} deviceKey -> pending cutoff timer */
const pendingCutoffs = new Map();

function deviceKey(floorId, deviceId) {
  return `${floorId}/${deviceId}`;
}

let previousFloors = null;

function clearCutoff(key) {
  const timer = pendingCutoffs.get(key);
  if (timer) {
    clearTimeout(timer);
    pendingCutoffs.delete(key);
  }
}

/**
 * Force-cut a safety-hazard device, write usage log + alert.
 */
async function forceCutoff(floorId, deviceId, device) {
  const key = deviceKey(floorId, deviceId);
  clearCutoff(key);

  const now = Date.now();
  const turnedOnAt = Number(device.last_turned_on_timestamp) || now;
  const durationSeconds = Math.max(0, Math.floor((now - turnedOnAt) / 1000));
  const deviceRef = db.ref(`house/floors/${floorId}/devices/${deviceId}`);

  try {
    await deviceRef.update({ status: "OFF" });

    const logRef = db.ref(`usage_logs/${deviceId}`).push();
    await logRef.set({
      turned_on_at: turnedOnAt,
      turned_off_at: now,
      duration_seconds: durationSeconds,
      auto_cutoff: true,
    });

    const alertRef = db.ref("alerts").push();
    await alertRef.set({
      device_id: deviceId,
      message: `${device.name || deviceId} exceeded max active duration and was auto-shut-off.`,
      timestamp: now,
      acknowledged: false,
    });

    console.log(
      `[cutoff] ${key} → OFF (on for ${durationSeconds}s, max ${device.max_on_duration_seconds}s)`
    );
  } catch (err) {
    console.error(`[cutoff] failed for ${key}:`, err.message);
  }
}

/**
 * Evaluate one SAFETY_HAZARD device after a database update.
 * Schedules a one-shot timer for remaining safe time; cuts immediately if already overdue.
 */
function evaluateSafetyDevice(floorId, deviceId, device) {
  const key = deviceKey(floorId, deviceId);
  clearCutoff(key);

  if (!device || device.type !== "SMART_SWITCH") return;
  if (device.status !== "ON") return;
  if (!device.auto_cutoff_enabled) return;

  const maxSeconds = Number(device.max_on_duration_seconds);
  if (!Number.isFinite(maxSeconds) || maxSeconds <= 0) {
    console.warn(`[safety] ${key} missing max_on_duration_seconds`);
    return;
  }

  const turnedOnAt = Number(device.last_turned_on_timestamp);
  if (!Number.isFinite(turnedOnAt) || turnedOnAt <= 0) {
    console.warn(`[safety] ${key} ON but missing last_turned_on_timestamp`);
    return;
  }

  const elapsedMs = Date.now() - turnedOnAt;
  const maxMs = maxSeconds * 1000;
  const remainingMs = maxMs - elapsedMs;

  if (remainingMs <= 0) {
    forceCutoff(floorId, deviceId, device);
    return;
  }

  console.log(
    `[safety] ${key} armed — auto-off in ${Math.ceil(remainingMs / 1000)}s`
  );

  const timer = setTimeout(() => {
    pendingCutoffs.delete(key);
    db.ref(`house/floors/${floorId}/devices/${deviceId}`)
      .once("value")
      .then((snap) => {
        const latest = snap.val();
        if (
          latest &&
          latest.type === "SMART_SWITCH" &&
          latest.auto_cutoff_enabled &&
          latest.status === "ON"
        ) {
          return forceCutoff(floorId, deviceId, latest);
        }
      })
      .catch((err) =>
        console.error(`[safety] re-read failed ${key}:`, err.message)
      );
  }, remainingMs);

  pendingCutoffs.set(key, timer);
}

function processHouseSnapshot(house) {
  const floors = house && house.floors ? house.floors : {};
  previousFloors = JSON.parse(JSON.stringify(floors)); // Deep copy to avoid reference mutations
  
  const seen = new Set();

  for (const [floorId, floor] of Object.entries(floors)) {
    const devices = floor && floor.devices ? floor.devices : {};
    for (const [deviceId, device] of Object.entries(devices)) {
      const key = deviceKey(floorId, deviceId);
      seen.add(key);
      evaluateSafetyDevice(floorId, deviceId, device);
    }
  }

  for (const key of pendingCutoffs.keys()) {
    if (!seen.has(key)) clearCutoff(key);
  }
}

function start() {
  console.log("[safety-worker] starting — listening on house/floors");

  const floorsRef = db.ref("house/floors");

  floorsRef.on(
    "value",
    (snapshot) => {
      const floors = snapshot.val();
      processHouseSnapshot({ floors: floors || {} });
    },
    (err) => {
      console.error("[safety-worker] listener error:", err.message);
    }
  );

  process.on("SIGINT", () => {
    console.log("\n[safety-worker] shutting down");
    for (const key of [...pendingCutoffs.keys()]) clearCutoff(key);
    floorsRef.off();
    process.exit(0);
  });
}

start();
