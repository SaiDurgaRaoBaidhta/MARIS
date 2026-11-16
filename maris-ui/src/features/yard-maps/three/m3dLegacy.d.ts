/* types of the ported 3D module (m3dLegacy.js, plain JavaScript kept close to the old viewer) */
export interface M3DStage {
  /** fit the stage to its box */
  size: (w: number, h: number) => void;
  /** stop rendering and free the WebGL context */
  dispose: () => void;
}
export interface M3DEnv {
  /** a drawn track's cars in the shape the 3D module reads: [car text, home, from, status, high PM, orientation] or 0 */
  track: (yard: string, track: string) => { id: string; desc: string; cap: number; cars: ([string, string, string, string, number, string] | 0)[] } | null;
  toast?: (msg: string, kind?: string) => void;
}
export interface M3D {
  build: (f: HTMLElement, spec: Record<string, unknown>, root: HTMLElement, view: HTMLElement) => M3DStage;
}
export function createM3D(env: M3DEnv): M3D;
