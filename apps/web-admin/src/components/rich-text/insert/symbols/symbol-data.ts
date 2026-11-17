/**
 * Danh sách ký hiệu của hộp "Ký hiệu và đơn vị" (spec 4.4, bản vẽ `SoanThaoKyHieu`). Tên hiển thị và tên của ngôn ngữ
 * kia (để tìm theo cả tiếng Việt lẫn tiếng Anh) nằm ở `messages/*.json`, khóa `richText.insertDialog.symbols.names.<id>`
 * và `.aliases.<id>`.
 */

export const SYMBOL_GROUP_IDS = ["greek", "operators", "arrows", "units", "biology", "ipa"] as const;
export type SymbolGroupId = (typeof SYMBOL_GROUP_IDS)[number];

/** Một đoạn của ký hiệu: chữ thường, chỉ số dưới hoặc chỉ số trên (chèn bằng `sub`/`sup` thật, không dùng chữ Unicode chỉ số). */
export type SymbolPart = string | { sub: string } | { sup: string };

export interface SymbolDef {
  /** Khóa ổn định: tên ở `messages`, và giá trị lưu ở "Dùng gần đây". */
  id: string;
  group: SymbolGroupId;
  parts: SymbolPart[];
}

function symbol(id: string, group: SymbolGroupId, ...parts: SymbolPart[]): SymbolDef {
  return { id, group, parts };
}

export const SYMBOLS: SymbolDef[] = [
  symbol("greekAlpha", "greek", "α"),
  symbol("greekBeta", "greek", "β"),
  symbol("greekGamma", "greek", "γ"),
  symbol("greekDelta", "greek", "δ"),
  symbol("greekEpsilon", "greek", "ε"),
  symbol("greekTheta", "greek", "θ"),
  symbol("greekLambda", "greek", "λ"),
  symbol("greekMu", "greek", "μ"),
  symbol("greekPi", "greek", "π"),
  symbol("greekRho", "greek", "ρ"),
  symbol("greekSigma", "greek", "σ"),
  symbol("greekPhi", "greek", "φ"),
  symbol("greekOmega", "greek", "ω"),
  symbol("greekCapitalDelta", "greek", "Δ"),
  symbol("greekCapitalSigma", "greek", "Σ"),
  symbol("greekCapitalPhi", "greek", "Φ"),
  symbol("greekCapitalOmega", "greek", "Ω"),

  symbol("plusMinus", "operators", "±"),
  symbol("times", "operators", "×"),
  symbol("divide", "operators", "÷"),
  symbol("middleDot", "operators", "·"),
  symbol("lessOrEqual", "operators", "≤"),
  symbol("greaterOrEqual", "operators", "≥"),
  symbol("notEqual", "operators", "≠"),
  symbol("approx", "operators", "≈"),
  symbol("identical", "operators", "≡"),
  symbol("infinity", "operators", "∞"),
  symbol("elementOf", "operators", "∈"),
  symbol("notElementOf", "operators", "∉"),
  symbol("subset", "operators", "⊂"),
  symbol("union", "operators", "∪"),
  symbol("intersection", "operators", "∩"),
  symbol("forAll", "operators", "∀"),
  symbol("exists", "operators", "∃"),
  symbol("perpendicular", "operators", "⊥"),
  symbol("parallel", "operators", "∥"),
  symbol("angle", "operators", "∠"),
  symbol("degree", "operators", "°"),

  symbol("arrowRight", "arrows", "→"),
  symbol("arrowLeft", "arrows", "←"),
  symbol("arrowBoth", "arrows", "↔"),
  symbol("implies", "arrows", "⇒"),
  symbol("iff", "arrows", "⇔"),
  symbol("arrowReversible", "arrows", "⇌"),
  symbol("arrowUp", "arrows", "↑"),
  symbol("arrowDown", "arrows", "↓"),

  symbol("unitMetrePerSecond", "units", "m/s"),
  symbol("unitMetrePerSecondSquared", "units", "m/s", { sup: "2" }),
  symbol("unitNewton", "units", "N"),
  symbol("unitJoule", "units", "J"),
  symbol("unitWatt", "units", "W"),
  symbol("unitPascal", "units", "Pa"),
  symbol("unitOhm", "units", "Ω"),
  symbol("unitVolt", "units", "V"),
  symbol("unitAmpere", "units", "A"),
  symbol("unitHertz", "units", "Hz"),
  symbol("unitCelsius", "units", "°C"),
  symbol("unitKelvin", "units", "K"),
  symbol("unitMole", "units", "mol"),
  symbol("unitMolePerLitre", "units", "mol/L"),
  symbol("unitKilojoulePerMole", "units", "kJ/mol"),
  symbol("unitMicrofarad", "units", "μF"),
  symbol("unitNanometre", "units", "nm"),
  symbol("unitElectronvolt", "units", "eV"),
  symbol("unitDecibel", "units", "dB"),
  symbol("unitWattPerSquareMetre", "units", "W/m", { sup: "2" }),

  symbol("bioMale", "biology", "♂"),
  symbol("bioFemale", "biology", "♀"),
  symbol("bioParental", "biology", "P"),
  symbol("bioGametes", "biology", "G"),
  symbol("bioFilial1", "biology", "F", { sub: "1" }),
  symbol("bioFilial2", "biology", "F", { sub: "2" }),
  symbol("bioAlleleXA", "biology", "X", { sup: "A" }),
  symbol("bioAlleleXa", "biology", "X", { sup: "a" }),
  symbol("bioY", "biology", "Y"),
  symbol("bioDiploid", "biology", "2n"),
  symbol("bioTriploid", "biology", "3n"),
  symbol("bioFivePrime", "biology", "5′"),
  symbol("bioThreePrime", "biology", "3′"),

  symbol("ipaSchwa", "ipa", "ə"),
  symbol("ipaShortI", "ipa", "ɪ"),
  symbol("ipaLongI", "ipa", "iː"),
  symbol("ipaShortU", "ipa", "ʊ"),
  symbol("ipaLongU", "ipa", "uː"),
  symbol("ipaE", "ipa", "e"),
  symbol("ipaAsh", "ipa", "æ"),
  symbol("ipaWedge", "ipa", "ʌ"),
  symbol("ipaLongA", "ipa", "ɑː"),
  symbol("ipaOpenO", "ipa", "ɒ"),
  symbol("ipaLongO", "ipa", "ɔː"),
  symbol("ipaLongSchwa", "ipa", "ɜː"),
  symbol("ipaTheta", "ipa", "θ"),
  symbol("ipaEth", "ipa", "ð"),
  symbol("ipaEsh", "ipa", "ʃ"),
  symbol("ipaYogh", "ipa", "ʒ"),
  symbol("ipaCh", "ipa", "tʃ"),
  symbol("ipaJ", "ipa", "dʒ"),
  symbol("ipaEng", "ipa", "ŋ"),
  symbol("ipaPrimaryStress", "ipa", "ˈ"),
  symbol("ipaSecondaryStress", "ipa", "ˌ"),
];

const SYMBOLS_BY_ID = new Map(SYMBOLS.map((entry) => [entry.id, entry]));

export function symbolById(id: string): SymbolDef | undefined {
  return SYMBOLS_BY_ID.get(id);
}

/** Chữ thuần của ký hiệu, các đoạn nối liền (dùng để tìm kiếm và đọc). */
export function symbolText(entry: SymbolDef): string {
  return entry.parts.map((part) => (typeof part === "string" ? part : "sub" in part ? part.sub : part.sup)).join("");
}
