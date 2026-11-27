/**
 * Danh sách mẫu của hộp công thức toán (spec 4.2): tám tab, mỗi tab một danh sách mã mục. Nội dung dữ liệu nằm ở đây,
 * không viết rải trong JSX. Tên mục (vi/en) ở `richText.insertDialog.math.palette.<id>` trong `messages/{vi,en}.json`.
 *
 * Mẫu viết bằng LaTeX; ô giữ chỗ là `\placeholder{}` của mathlive (bấm nút thì con trỏ nhảy tới ô đầu tiên).
 */

/** Ô giữ chỗ của mathlive trong mẫu LaTeX. */
export const PLACEHOLDER = "\\placeholder{}";

/** Mã tab, trùng khóa `richText.insertDialog.math.tabs.<id>`. */
export type PaletteTabId = "common" | "fractionRoot" | "power" | "limitIntegral" | "matrix" | "geometry" | "sets" | "greek";

export interface PaletteTab {
  id: PaletteTabId;
  /** Mã các mẫu của tab theo thứ tự hiện; tra mẫu ở `PALETTE_LATEX`. */
  items: readonly string[];
}

/** Mẫu LaTeX theo mã mục (một mục có thể nằm ở nhiều tab). */
export const PALETTE_LATEX: Readonly<Record<string, string>> = {
  frac: "\\frac{\\placeholder{}}{\\placeholder{}}",
  sqrt: "\\sqrt{\\placeholder{}}",
  nthRoot: "\\sqrt[\\placeholder{}]{\\placeholder{}}",
  power: "\\placeholder{}^{\\placeholder{}}",
  subscript: "\\placeholder{}_{\\placeholder{}}",
  absolute: "\\left|\\placeholder{}\\right|",
  plusMinus: "\\pm",
  times: "\\times",
  divide: "\\div",
  notEqual: "\\neq",
  lessEqual: "\\leq",
  greaterEqual: "\\geq",
  approx: "\\approx",
  infinity: "\\infty",
  pi: "\\pi",
  degree: "^{\\circ}",
  fracOneOver: "\\frac{1}{\\placeholder{}}",
  fracHalf: "\\frac{1}{2}",
  fracOfPi: "\\frac{\\pi}{\\placeholder{}}",
  fracOfRoot: "\\frac{\\sqrt{\\placeholder{}}}{\\placeholder{}}",
  cubeRoot: "\\sqrt[3]{\\placeholder{}}",
  sqrtOfFrac: "\\sqrt{\\frac{\\placeholder{}}{\\placeholder{}}}",
  binomial: "\\binom{\\placeholder{}}{\\placeholder{}}",
  mixedNumber: "\\placeholder{}\\frac{\\placeholder{}}{\\placeholder{}}",
  percent: "\\%",
  square: "\\placeholder{}^{2}",
  cube: "\\placeholder{}^{3}",
  inverse: "\\placeholder{}^{-1}",
  subSup: "\\placeholder{}_{\\placeholder{}}^{\\placeholder{}}",
  eulerPower: "e^{\\placeholder{}}",
  tenPower: "10^{\\placeholder{}}",
  overline: "\\overline{\\placeholder{}}",
  underline: "\\underline{\\placeholder{}}",
  hat: "\\hat{\\placeholder{}}",
  prime: "\\placeholder{}^{\\prime}",
  doublePrime: "\\placeholder{}^{\\prime\\prime}",
  underBrace: "\\underbrace{\\placeholder{}}_{\\placeholder{}}",
  limit: "\\lim_{\\placeholder{} \\to \\placeholder{}} \\placeholder{}",
  limitInfinity: "\\lim_{x \\to +\\infty} \\placeholder{}",
  limitRight: "\\lim_{x \\to \\placeholder{}^{+}} \\placeholder{}",
  limitLeft: "\\lim_{x \\to \\placeholder{}^{-}} \\placeholder{}",
  antiderivative: "\\int \\placeholder{} \\, \\mathrm{d}x",
  definiteIntegral: "\\int_{\\placeholder{}}^{\\placeholder{}} \\placeholder{} \\, \\mathrm{d}x",
  doubleIntegral: "\\iint \\placeholder{} \\, \\mathrm{d}x \\, \\mathrm{d}y",
  sum: "\\sum_{k=1}^{n} \\placeholder{}",
  product: "\\prod_{k=1}^{n} \\placeholder{}",
  derivative: "\\frac{\\mathrm{d}}{\\mathrm{d}x}",
  partialDerivative: "\\frac{\\partial \\placeholder{}}{\\partial \\placeholder{}}",
  firstDerivative: "f^{\\prime}(x)",
  secondDerivative: "f^{\\prime\\prime}(x)",
  tendsTo: "\\to",
  naturalLog: "\\ln \\placeholder{}",
  logBase: "\\log_{\\placeholder{}} \\placeholder{}",
  system2: "\\begin{cases} \\placeholder{} \\\\ \\placeholder{} \\end{cases}",
  system3: "\\begin{cases} \\placeholder{} \\\\ \\placeholder{} \\\\ \\placeholder{} \\end{cases}",
  matrix2: "\\begin{pmatrix} \\placeholder{} & \\placeholder{} \\\\ \\placeholder{} & \\placeholder{} \\end{pmatrix}",
  matrix3: "\\begin{pmatrix} \\placeholder{} & \\placeholder{} & \\placeholder{} \\\\ \\placeholder{} & \\placeholder{} & \\placeholder{} \\\\ \\placeholder{} & \\placeholder{} & \\placeholder{} \\end{pmatrix}",
  matrixRow: "\\begin{pmatrix} \\placeholder{} & \\placeholder{} & \\placeholder{} \\end{pmatrix}",
  matrixColumn: "\\begin{pmatrix} \\placeholder{} \\\\ \\placeholder{} \\\\ \\placeholder{} \\end{pmatrix}",
  matrixBracket2: "\\begin{bmatrix} \\placeholder{} & \\placeholder{} \\\\ \\placeholder{} & \\placeholder{} \\end{bmatrix}",
  determinant2: "\\begin{vmatrix} \\placeholder{} & \\placeholder{} \\\\ \\placeholder{} & \\placeholder{} \\end{vmatrix}",
  determinant3: "\\begin{vmatrix} \\placeholder{} & \\placeholder{} & \\placeholder{} \\\\ \\placeholder{} & \\placeholder{} & \\placeholder{} \\\\ \\placeholder{} & \\placeholder{} & \\placeholder{} \\end{vmatrix}",
  aligned: "\\begin{aligned} \\placeholder{} &= \\placeholder{} \\\\ &= \\placeholder{} \\end{aligned}",
  vectorColumn2: "\\begin{pmatrix} \\placeholder{} \\\\ \\placeholder{} \\end{pmatrix}",
  transpose: "\\placeholder{}^{\\mathsf{T}}",
  vector: "\\overrightarrow{\\placeholder{}}",
  vectorSingle: "\\vec{\\placeholder{}}",
  segment: "\\overline{\\placeholder{}}",
  angle: "\\widehat{\\placeholder{}}",
  angleSymbol: "\\angle",
  triangle: "\\triangle",
  perpendicular: "\\perp",
  parallel: "\\parallel",
  congruent: "\\cong",
  similar: "\\sim",
  dotProduct: "\\cdot",
  crossProduct: "\\times",
  norm: "\\left|\\overrightarrow{\\placeholder{}}\\right|",
  arc: "\\overset{\\frown}{\\placeholder{}}",
  element: "\\in",
  notElement: "\\notin",
  subset: "\\subset",
  subsetEqual: "\\subseteq",
  union: "\\cup",
  intersection: "\\cap",
  emptySet: "\\varnothing",
  setMinus: "\\setminus",
  setBraces: "\\left\\{\\placeholder{}\\right\\}",
  intervalClosed: "\\left[\\placeholder{};\\placeholder{}\\right]",
  intervalOpen: "\\left(\\placeholder{};\\placeholder{}\\right)",
  naturals: "\\mathbb{N}",
  integers: "\\mathbb{Z}",
  rationals: "\\mathbb{Q}",
  reals: "\\mathbb{R}",
  complexes: "\\mathbb{C}",
  forAll: "\\forall",
  exists: "\\exists",
  not: "\\neg",
  and: "\\wedge",
  or: "\\vee",
  implies: "\\Rightarrow",
  iff: "\\Leftrightarrow",
  alpha: "\\alpha",
  beta: "\\beta",
  gamma: "\\gamma",
  delta: "\\delta",
  epsilon: "\\varepsilon",
  zeta: "\\zeta",
  eta: "\\eta",
  theta: "\\theta",
  iota: "\\iota",
  kappa: "\\kappa",
  lambda: "\\lambda",
  mu: "\\mu",
  nu: "\\nu",
  xi: "\\xi",
  rho: "\\rho",
  sigma: "\\sigma",
  tau: "\\tau",
  upsilon: "\\upsilon",
  phi: "\\varphi",
  chi: "\\chi",
  psi: "\\psi",
  omega: "\\omega",
  capitalGamma: "\\Gamma",
  capitalDelta: "\\Delta",
  capitalTheta: "\\Theta",
  capitalLambda: "\\Lambda",
  capitalXi: "\\Xi",
  capitalPi: "\\Pi",
  capitalSigma: "\\Sigma",
  capitalPhi: "\\Phi",
  capitalPsi: "\\Psi",
  capitalOmega: "\\Omega",
};

/**
 * Mẫu gọn để vẽ mặt nút khi mẫu đầy đủ quá to so với ô 56px (giới hạn, tích phân, tổng, tích): giới hạn xếp dưới
 * chữ `lim`/`∑` như bản vẽ, bỏ phần `dx` thừa. Mục không có ở đây vẽ bằng chính `PALETTE_LATEX`. Chỉ để xem, không
 * chèn vào ô soạn.
 */
export const PALETTE_GLYPH_LATEX: Readonly<Record<string, string>> = {
  limit: "\\displaystyle\\lim_{\\placeholder{}\\to\\placeholder{}}\\placeholder{}",
  limitInfinity: "\\displaystyle\\lim_{x\\to+\\infty}\\placeholder{}",
  limitRight: "\\displaystyle\\lim_{x\\to\\placeholder{}^{+}}\\placeholder{}",
  limitLeft: "\\displaystyle\\lim_{x\\to\\placeholder{}^{-}}\\placeholder{}",
  definiteIntegral: "\\int_{a}^{b}\\placeholder{}",
  doubleIntegral: "\\iint\\placeholder{}",
  sum: "\\displaystyle\\sum_{k=1}^{n}",
  product: "\\displaystyle\\prod_{k=1}^{n}",
};

/**
 * Khoảng cách (UNI-2878): LaTeX bỏ qua dấu cách gõ thường nên chèn lệnh. `\ ` rộng một dấu cách chữ, `\quad` rộng một
 * chữ M để tách hai công thức đặt cạnh nhau. Không nằm trong bảng mẫu: hai nút chữ nhỏ ở góc ô soạn trực quan.
 */
export const SPACING_LATEX = {
  space: "\\ ",
  wideSpace: "\\quad",
} as const;

export type SpacingId = keyof typeof SPACING_LATEX;

export const PALETTE_TABS: readonly PaletteTab[] = [
  {
    id: "common",
    items: ["frac", "sqrt", "nthRoot", "power", "subscript", "absolute", "plusMinus", "times", "divide", "notEqual", "lessEqual", "greaterEqual", "approx", "infinity", "pi", "degree"],
  },
  {
    id: "fractionRoot",
    items: ["frac", "fracOneOver", "fracHalf", "fracOfPi", "fracOfRoot", "sqrt", "cubeRoot", "nthRoot", "sqrtOfFrac", "binomial", "mixedNumber", "percent"],
  },
  {
    id: "power",
    items: ["power", "square", "cube", "inverse", "subscript", "subSup", "eulerPower", "tenPower", "overline", "underline", "hat", "prime", "doublePrime", "underBrace"],
  },
  {
    id: "limitIntegral",
    items: ["limit", "limitInfinity", "limitRight", "limitLeft", "antiderivative", "definiteIntegral", "doubleIntegral", "sum", "product", "derivative", "partialDerivative", "firstDerivative", "secondDerivative", "infinity", "tendsTo", "eulerPower", "naturalLog", "logBase", "absolute", "sqrt"],
  },
  {
    id: "matrix",
    items: ["system2", "system3", "matrix2", "matrix3", "matrixRow", "matrixColumn", "matrixBracket2", "determinant2", "determinant3", "aligned", "vectorColumn2", "transpose"],
  },
  {
    id: "geometry",
    items: ["vector", "vectorSingle", "segment", "angle", "angleSymbol", "triangle", "perpendicular", "parallel", "congruent", "similar", "degree", "dotProduct", "crossProduct", "norm", "arc", "pi"],
  },
  {
    id: "sets",
    items: ["element", "notElement", "subset", "subsetEqual", "union", "intersection", "emptySet", "setMinus", "setBraces", "intervalClosed", "intervalOpen", "naturals", "integers", "rationals", "reals", "complexes", "forAll", "exists", "not", "and", "or", "implies", "iff"],
  },
  {
    id: "greek",
    items: ["alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu", "nu", "xi", "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega", "capitalGamma", "capitalDelta", "capitalTheta", "capitalLambda", "capitalXi", "capitalPi", "capitalSigma", "capitalPhi", "capitalPsi", "capitalOmega"],
  },
];

/** Mẫu dùng để vẽ mặt nút: ô giữ chỗ đổi thành ô vuông rỗng (KaTeX không có `\\placeholder`). */
export function paletteGlyphLatex(latex: string): string {
  return latex.split(PLACEHOLDER).join("\\square");
}
