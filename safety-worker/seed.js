/**
 * Seeds Firebase Realtime Database from docs/02-database-schema.md
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

const seed = {
  house: {
    floors: {
      floor_1: {
        id: "floor_1",
        name: "Ground Floor",
        grid_layout: "3x3",
        devices: {
          dev_01: {
            id: "dev_01",
            name: "Living Room Light",
            type: "LIGHTING",
            status: "ON",
            grid_position: { x: 0, y: 1 },
            is_scheduled: true,
            schedule: {
              turn_on_time: "18:00",
              turn_off_time: "22:00",
            },
          },
          dev_02: {
            id: "dev_02",
            name: "Clothing Iron",
            type: "SMART_SWITCH",
            status: "OFF",
            grid_position: { x: 1, y: 2 },
            auto_cutoff_enabled: true,
            max_on_duration_seconds: 1800,
            last_turned_on_timestamp: 0,
          },
          dev_03: {
            id: "dev_03",
            name: "Main Gang Box",
            type: "MULTI_SWITCH",
            status: "ON",
            grid_position: { x: 2, y: 0 },
            switches: {
              sw_1: { name: "Fan", status: "ON" },
              sw_2: { name: "Chandelier", status: "OFF" },
              sw_3: { name: "Wall Sconce", status: "OFF" },
            },
          },
          dev_04: {
            id: "dev_04",
            name: "Kitchen Outlet",
            type: "SMART_SWITCH",
            status: "ON",
            grid_position: { x: 0, y: 2 },
            auto_cutoff_enabled: false,
          },
          dev_05: {
            id: "dev_05",
            name: "Front Door Camera",
            type: "SECURITY_CAMERA",
            status: "ON",
            grid_position: { x: 2, y: 2 },
            mock_stream_uri:
              "https://maitv-vod.lab.eyevinn.technology/VINN.mp4/master.m3u8",
            last_snapshot_timestamp: 1722254000000,
          },
        },
      },
      floor_2: {
        id: "floor_2",
        name: "First Floor",
        grid_layout: "3x3",
        devices: {
          dev_06: {
            id: "dev_06",
            name: "Bedroom Light",
            type: "LIGHTING",
            status: "OFF",
            grid_position: { x: 1, y: 0 },
            is_scheduled: false,
          },
          dev_07: {
            id: "dev_07",
            name: "Hallway Camera",
            type: "SECURITY_CAMERA",
            status: "OFF",
            grid_position: { x: 0, y: 0 },
            mock_stream_uri:
              "https://maitv-vod.lab.eyevinn.technology/VINN.mp4/master.m3u8",
            last_snapshot_timestamp: 1722254000000,
          },
        },
      },
    },
  },
  usage_logs: {
    dev_02: {
      log_001: {
        turned_on_at: 1722254000000,
        turned_off_at: 1722255800000,
        duration_seconds: 1800,
        auto_cutoff: true,
      },
    },
    dev_01: {
      log_001: {
        turned_on_at: 1722232800000,
        turned_off_at: 1722247200000,
        duration_seconds: 14400,
        auto_cutoff: false,
      },
    },
  },
  alerts: {
    alert_001: {
      device_id: "dev_02",
      message:
        "Clothing Iron exceeded max active duration and was auto-shut-off.",
      timestamp: 1722255800000,
      acknowledged: false,
    },
  },
};

async function main() {
  await getDatabase(app).ref().set(seed);
  console.log("Database seeded from docs/02-database-schema.md");
  process.exit(0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
