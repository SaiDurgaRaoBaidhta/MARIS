/* Lay-up (old VHLT01I) and reconfigure (old VHTR01I with its confirmation VHTR02I): /api/v1/consist. */
import { get, post } from "@/shared/api/client";

export interface LayUpOptions { yards: string[]; yard: string; time: string; revenueDate: string }
export interface LayUpTrain { consist: string; layUp: boolean; pseudoConsist: string; message: string }
export interface Message { message: string }

export const fetchLayUpOptions = () => get<LayUpOptions>("/consist/lay-up/options");
export const fetchLayUpTracks = (yard: string) => get<string[]>(`/consist/lay-up/tracks?${new URLSearchParams({ yard })}`);
export const checkLayUpTrain = (consist: string) => get<LayUpTrain>(`/consist/lay-up/trains/${encodeURIComponent(consist)}`);
export const layUpTrain = (r: { yard: string; track: string; consist: string; time: string }) => post<Message>("/consist/lay-up", r);

/** the user's steps on the old window: 1 = Build-Up, 2 = Service Reduction; splits = the car positions of the split buttons pressed */
export interface Steps { action: 1 | 2; consist: string; leftConsist?: string; rightConsist?: string; pseudo?: string | null; splits: number[] }
export interface ReconfigureOptions { yards: string[]; time: string; splitPositions: number[] }
export interface Consist { consist: string; cars: string[] }
export interface ReconfigureState { cars: string[]; left: Consist; right: Consist; newPseudo: string; reconfigure: boolean; split: boolean;
  pseudoEnabled: boolean; message: string }
export interface ReconfigureResult { message: string; question?: string | null }

export const fetchReconfigureOptions = () => get<ReconfigureOptions>("/consist/reconfigure/options");
export const fetchReconfigureTracks = (yard: string) => get<string[]>(`/consist/reconfigure/tracks?${new URLSearchParams({ yard })}`);
export const reconfigureState = (steps: Steps) => post<ReconfigureState>("/consist/reconfigure/state", steps);
export const reconfigureTrain = (r: { steps: Steps; yard: string; track: string; time: string; confirmed: boolean }) =>
  post<ReconfigureResult>("/consist/reconfigure", r);
