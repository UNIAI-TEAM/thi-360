/**
 * Bốn bộ tự sửa của "Dán từ Word" (spec 4.10, bản vẽ `SoanThaoDanTuWord`), chạy trên LaTeX thô mà `omml.ts` trả:
 *
 * 1. `functionName`: tên hàm bị tách chữ (`s i n`, `sin` viết như biến) → `\sin`.
 * 2. `orSystem`: hệ "hoặc" bị Word đổi thành phân số (`\left[\frac{A}{B}\right.`) → `\left[\begin{array}{l} A \\ B \end{array}\right.`.
 * 3. `chemistry`: công thức hóa học bị bọc ngoặc (`\left(H_2SO_4\right)`) → `\ce{H2SO4}`.
 * 4. `degree`: ký hiệu độ viết bằng chữ (`30°`) → `30^{\circ}`.
 *
 * Mỗi chỗ sửa được đánh dấu chờ xác nhận (xem `session.ts`): người soạn Chấp nhận hoặc Sửa tay. Bộ sửa chỉ đổi khi
 * nhận ra đúng mẫu; không chắc thì để nguyên.
 */
import { FUNCTION_NAMES, functionCommand } from "./latex-symbols";

export type FixKind = "functionName" | "orSystem" | "chemistry" | "degree";

export interface AutoFix {
  /** LaTeX sau khi sửa (bằng đầu vào nếu không có chỗ nào sửa). */
  latex: string;
  /** Các loại sửa đã áp dụng, theo thứ tự áp dụng; rỗng khi không sửa gì. */
  kinds: FixKind[];
}

/** Lệnh mà nội dung `{…}` theo sau là chữ thường, không phải biến toán: tên hàm trong đó không được sửa. */
const TEXT_COMMANDS: Readonly<Record<string, true>> = {
  text: true,
  mathrm: true,
  mathbf: true,
  boldsymbol: true,
  operatorname: true,
  textrm: true,
  textbf: true,
  ce: true,
  begin: true,
  end: true,
};

/** Tên hàm dài trước để `arcsin` thắng `sin`. */
const FUNCTIONS_LONGEST_FIRST = [...FUNCTION_NAMES].sort((a, b) => b.length - a.length);

/** Vị trí `}` đóng nhóm mở ở `open` (`s[open] === "{"`); `-1` nếu không khép. */
function groupEnd(s: string, open: number): number {
  let depth = 0;
  for (let i = open; i < s.length; i++) {
    if (s[i] === "\\") {
      i++;
    } else if (s[i] === "{") {
      depth++;
    } else if (s[i] === "}") {
      depth--;
      if (depth === 0) return i;
    }
  }
  return -1;
}

/**
 * Áp `transform` lên mọi đoạn chữ "tự do" của LaTeX: bỏ qua lệnh (`\alpha`) và nội dung nhóm của lệnh chữ thường
 * (`\text{…}`, `\mathrm{…}`).
 */
function mapFreeText(latex: string, transform: (text: string) => string): string {
  let out = "";
  let i = 0;
  while (i < latex.length) {
    if (latex[i] === "\\") {
      const command = /^\\([A-Za-z]+)/.exec(latex.slice(i));
      if (!command) {
        out += latex.slice(i, i + 2);
        i += 2;
        continue;
      }
      out += command[0];
      i += command[0].length;
      if (TEXT_COMMANDS[command[1]!] && latex[i] === "{") {
        const end = groupEnd(latex, i);
        const stop = end === -1 ? latex.length : end + 1;
        out += latex.slice(i, stop);
        i = stop;
      }
      continue;
    }
    let j = i;
    while (j < latex.length && latex[j] !== "\\") j++;
    out += transform(latex.slice(i, j));
    i = j;
  }
  return out;
}

/** Chữ cái đầu của dãy chữ cái (cách nhau tối đa một khoảng trắng) là tên hàm → viết lại thành `\name`. */
function fixFunctionNames(latex: string): string {
  return mapFreeText(latex, (text) =>
    text.replace(/[A-Za-z](?: ?[A-Za-z])*/g, (run) => {
      for (const name of FUNCTIONS_LONGEST_FIRST) {
        const spaced = [...name].join(" ");
        const literal = run.startsWith(name) ? name : run.startsWith(spaced) ? spaced : null;
        if (literal === null) continue;
        const rest = run.slice(literal.length).trimStart();
        return rest ? `${functionCommand(name)} ${rest}` : functionCommand(name);
      }
      return run;
    }),
  );
}

/**
 * Ký hiệu độ → `^{\circ}`: ký tự `°`/`º`/`˚`, hoặc số mũ chữ "o" sau một số (`30^o`, gõ tay trong Word).
 * `x^0` và `x^o` sau biến không đổi: có thể là lũy thừa thật.
 */
function fixDegree(latex: string): string {
  return latex.replace(/[°º˚]/g, "^{\\circ}").replace(/(\d+|\{\d+\})\^(?:o|\{o\})(?![A-Za-z0-9])/g, "$1^{\\circ}");
}

const RELATION = /=|\\neq|\\leq|\\geq|<|>/;

/**
 * Hệ "hoặc": cả công thức là `\left[ \frac{A}{B} \right.` (hoặc không có `\right.`) với A và B đều là quan hệ
 * (`m=0`, `m=\frac{8}{15}`) → mảng hai dòng trong ngoặc vuông mở. Phân số thật có tử/mẫu không phải quan hệ nên không đụng.
 */
function fixOrSystem(latex: string): string | null {
  const head = /^\\left\[\s*\\frac\{/.exec(latex);
  if (!head) return null;
  const numeratorOpen = head[0].length - 1;
  const numeratorEnd = groupEnd(latex, numeratorOpen);
  if (numeratorEnd === -1 || latex[numeratorEnd + 1] !== "{") return null;
  const denominatorEnd = groupEnd(latex, numeratorEnd + 1);
  if (denominatorEnd === -1) return null;
  const tail = latex.slice(denominatorEnd + 1).trim();
  if (tail !== "" && tail !== "\\right.") return null;
  const numerator = latex.slice(numeratorOpen + 1, numeratorEnd).trim();
  const denominator = latex.slice(numeratorEnd + 2, denominatorEnd).trim();
  if (!RELATION.test(numerator) || !RELATION.test(denominator)) return null;
  return `\\left[\\begin{array}{l} ${numerator} \\\\ ${denominator} \\end{array}\\right.`;
}

const ELEMENTS = new Set(
  "H He Li Be B C N O F Ne Na Mg Al Si P S Cl Ar K Ca Sc Ti V Cr Mn Fe Co Ni Cu Zn Ga Ge As Se Br Kr Rb Sr Y Zr Nb Mo Tc Ru Rh Pd Ag Cd In Sn Sb Te I Xe Cs Ba La Ce Pr Nd Pm Sm Eu Gd Tb Dy Ho Er Tm Yb Lu Hf Ta W Re Os Ir Pt Au Hg Tl Pb Bi Po At Rn Fr Ra Ac Th Pa U Np Pu Am Cm Bk Cf Es Fm Md No Lr Rf Db Sg Bh Hs Mt Ds Rg Cn Nh Fl Mc Lv Ts Og".split(" "),
);

/** Bỏ vỏ `\left( … \right)` hay `\left[ … \right]` bao trọn chuỗi; `null` nếu không bao trọn. */
function unwrapBrackets(latex: string): string | null {
  const open = /^\\left([([])/.exec(latex);
  if (!open) return null;
  const close = open[1] === "(" ? ")" : "]";
  if (!latex.endsWith(`\\right${close}`)) return null;
  const inner = latex.slice(open[0].length, latex.length - `\\right${close}`.length);
  // `\left(a\right)+\left(b\right)`: vỏ đầu không khép ở cuối chuỗi.
  let depth = 0;
  for (const match of inner.matchAll(/\\(left|right)(?![A-Za-z])/g)) {
    depth += match[1] === "left" ? 1 : -1;
    if (depth < 0) return null;
  }
  return depth === 0 ? inner.trim() : null;
}

/** LaTeX hóa học kiểu toán (`H_2SO_4`, `2H_2 + O_2 \rightarrow 2H_2O`) → mhchem; `null` nếu không phải công thức hóa học. */
function toChemistry(inner: string): string | null {
  const normalized = inner
    .replace(/\\mathrm\{([^{}]*)\}/g, "$1")
    .replace(/\\left([([])/g, "$1")
    .replace(/\\right([)\]])/g, "$1")
    .replace(/\{\}/g, "")
    .replace(/_\{(\d+)\}|_(\d)/g, (_all, braced: string | undefined, plain: string | undefined) => braced ?? plain ?? "")
    .replace(/\^\{([^{}]*)\}/g, "^$1")
    .replace(/\\(?:rightarrow|longrightarrow|to)(?![A-Za-z])/g, "->")
    .replace(/\\(?:rightleftharpoons|leftrightarrow)(?![A-Za-z])/g, "<=>")
    .replace(/\s+/g, " ")
    .trim();
  if (normalized === "") return null;

  let rest = normalized;
  let evidence = false;
  let elementCount = 0;
  // Số ngay sau ký hiệu nguyên tố hoặc ")" là chỉ số dưới; số đứng đầu một chất là hệ số.
  let followsSymbol = false;
  const consume = (pattern: RegExp): RegExpExecArray | null => {
    const match = pattern.exec(rest);
    if (match) rest = rest.slice(match[0].length);
    return match;
  };
  while (rest !== "") {
    if (consume(/^\d+/)) {
      if (followsSymbol) evidence = true;
      followsSymbol = false;
      continue;
    }
    if (consume(/^ +/) || consume(/^\++/)) {
      followsSymbol = false;
      continue;
    }
    if (consume(/^(?:->|<=>)/) || consume(/^\^\d*[+-]/) || consume(/^\((?:s|l|g|aq)\)/)) {
      evidence = true;
      followsSymbol = false;
      continue;
    }
    const paren = consume(/^[()]/);
    if (paren) {
      followsSymbol = paren[0] === ")";
      continue;
    }
    const element = /^[A-Z][a-z]?/.exec(rest);
    if (!element) return null;
    const symbol = ELEMENTS.has(element[0]) ? element[0] : ELEMENTS.has(element[0][0]!) ? element[0][0]! : null;
    if (!symbol) return null;
    rest = rest.slice(symbol.length);
    elementCount++;
    followsSymbol = true;
  }
  // Không có chỉ số, mũi tên, điện tích hay trạng thái thì (SBC), (C), (P) có thể là mặt phẳng/đường tròn: không đoán.
  return elementCount > 0 && evidence ? `\\ce{${normalized}}` : null;
}

function fixChemistry(latex: string): string | null {
  const inner = unwrapBrackets(latex);
  return inner === null ? null : toChemistry(inner);
}

/** Chạy bốn bộ tự sửa lên LaTeX thô của một công thức. */
export function autoFix(latex: string): AutoFix {
  let current = latex.trim();
  const kinds: FixKind[] = [];
  const apply = (kind: FixKind, next: string | null) => {
    if (next === null || next === current) return;
    current = next;
    kinds.push(kind);
  };

  apply("orSystem", fixOrSystem(current));
  apply("chemistry", fixChemistry(current));
  apply("functionName", fixFunctionNames(current));
  apply("degree", fixDegree(current));
  return { latex: current, kinds };
}
