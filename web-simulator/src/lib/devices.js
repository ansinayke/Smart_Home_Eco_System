/** Shared device helpers for the hardware simulator. */

export const STATUS_STYLES = {
  ON: {
    ring: "ring-emerald-400",
    glow: "shadow-[0_0_24px_rgba(52,211,153,0.45)]",
    badge: "bg-emerald-500 text-white",
    label: "ON",
  },
  OFF: {
    ring: "ring-slate-500",
    glow: "",
    badge: "bg-slate-600 text-slate-100",
    label: "OFF",
  },
  ERROR: {
    ring: "ring-rose-500",
    glow: "shadow-[0_0_24px_rgba(244,63,94,0.4)]",
    badge: "bg-rose-600 text-white",
    label: "ERROR",
  },
  DISCONNECTED: {
    ring: "ring-amber-500",
    glow: "",
    badge: "bg-amber-600 text-white",
    label: "DISCONNECTED",
  },
};

export function statusStyle(status) {
  return STATUS_STYLES[status] || STATUS_STYLES.DISCONNECTED;
}

export function typeLabel(type) {
  switch (type) {
    case "LIGHTING":
      return "Lighting";
    case "SMART_SWITCH":
      return "Smart Switch";
    case "MULTI_SWITCH":
      return "Multi-Switch";
    case "SECURITY_CAMERA":
      return "Camera";
    default:
      return type || "Device";
  }
}

export function flattenDevices(floors) {
  if (!floors) return [];
  const list = [];
  for (const [floorId, floor] of Object.entries(floors)) {
    const devices = floor?.devices || {};
    for (const [deviceId, device] of Object.entries(devices)) {
      list.push({
        ...device,
        id: device.id || deviceId,
        floorId,
        floorName: floor.name || floorId,
      });
    }
  }
  return list.sort((a, b) => a.id.localeCompare(b.id));
}
