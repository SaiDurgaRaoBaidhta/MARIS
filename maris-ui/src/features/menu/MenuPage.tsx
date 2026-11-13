import { useApp } from "@/shared/lib/app-state";
import { PageHead } from "@/shared/ui/PageHead";
import { MenuTiles } from "./MenuTiles";
import type { Section } from "./types";

export function MenuPage({ section }: { section: Section }) {
  const app = useApp();
  const code = / - /.test(section.title) ? section.title.split(" - ")[0] : section.to;
  const title = section.title.replace(/^\S+\s+-\s+/, "") || section.cap;
  const groups = section.groups.map(g => ({ name: g.name, list: g.list.filter(it => !it.dis).map(it => ({
    label: it.label, i: it.i, open: it.key ? () => app.open({ kind: "screen", key: it.key!, label: it.label }) : undefined })) })).filter(g => g.list.length);
  return <><PageHead title={title} code={code} ver="V.DEC.13.17" /><MenuTiles groups={groups} /></>;
}
