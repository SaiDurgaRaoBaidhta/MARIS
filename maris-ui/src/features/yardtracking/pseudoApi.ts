/* Pseudo trains: the middleware's clean API (/api/v1/yard-tracking). Old screens VHYT09I (pseudo status train) with its
 * printer window VHYT01I, Vhyt07i (Hayward yard map) and VHYT12I (verify / reset a pseudo train). */
import { get, post } from "@/shared/api/client";
import type { YardCar, YardTrack } from "@/features/yardreports/api";

export interface PseudoOptions { yards: string[]; yard: string; message: string; pageSize: number }
export interface PseudoCar { position: number; car: string; homeYard: string; colour: string }
export interface PseudoConsist { consist: string; track: string; yard: string; dateLastUpdated: string; timeLastUpdated: string; cars: PseudoCar[] }
export interface PseudoReport { yard: string; consists: PseudoConsist[]; message: string; printable: boolean }
export interface Printer { description: string; vtam: string }

const q = (params: Record<string, string>) => "?" + new URLSearchParams(params).toString();

export const pseudoOptions = (yard?: string) => get<PseudoOptions>("/yard-tracking/pseudo-trains/options" + (yard ? q({ yard }) : ""));
export const pseudoTrains = (yard: string) => get<PseudoReport>("/yard-tracking/pseudo-trains" + q({ yard }));
export const pseudoPrinters = () => get<Printer[]>("/yard-tracking/pseudo-trains/printers");
export const printPseudoTrains = (yard: string, printer: string) => post<{ submitted: boolean }>("/yard-tracking/pseudo-trains/print", { yard, printer });

export interface HaywardSpot { caption: string; track: string }
export interface HaywardYardMap { screen: string; yard: string; tracks: YardTrack[]; cars: YardCar[]; spots: HaywardSpot[]; pseudoTrainYard: string; printYard: string }
export const haywardYardMap = () => get<HaywardYardMap>("/yard-tracking/hayward-yard-map");

export interface VerifyCar { position: number; car: string; colour: string; consist: string; consistColour: string; location: string; homeYard: string }
export interface VerifyQuestion { key: string; title: string; text: string }
export interface VerifyState { cars: VerifyCar[]; consist: string; message: string; verifyEnabled: boolean; resetEnabled: boolean;
  question: VerifyQuestion | null; notice: string | null; closed: boolean }
export interface VerifyRequest { yard: string; track: string; fromPosition: number; toPosition: number; consist?: string; answers?: Record<string, string> }
export const openVerify = (r: VerifyRequest) => post<VerifyState>("/yard-tracking/consist-verify/open", r);
export const verifyConsist = (r: VerifyRequest) => post<VerifyState>("/yard-tracking/consist-verify/verify", r);
export const resetConsist = (r: VerifyRequest) => post<VerifyState>("/yard-tracking/consist-verify/reset", r);
