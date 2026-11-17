// Assign a stable, non-null templateGroupCode to every block of a template.
// Codes reuse the seeded HSA_* / HSA_*_REMAINDER set where the group still
// exists; new codes follow <KIND>_<SUBJECT>_<GROUP>.
// assign(template, resolve) walks every block location (part sections,
// part.subjectPool, option.sections and option.blocks) and calls
//   resolve({block, subjectCode, optionCode}) -> code or null.
// A null return throws so a renamed block can never silently lose its group.
export function assignGroups(template, resolve) {
  const seen = new Map();
  function visit(block, context) {
    if (!block || typeof block !== 'object') return;
    if (!('questionType' in block || 'questionCount' in block)) return;
    const code = resolve({ block, ...context });
    if (!code) throw new Error(`${template.code}: no templateGroupCode for block "${block.name}" in ${JSON.stringify(context)}`);
    block.templateGroupCode = code;
    seen.set(code, (seen.get(code) ?? 0) + 1);
  }
  for (const part of template.structure?.parts ?? []) {
    for (const section of part.sections ?? [])
      for (const block of section.blocks ?? []) visit(block, { subjectCode: section.subjectCode });
    for (const section of part.subjectPool ?? [])
      for (const block of section.blocks ?? []) visit(block, { subjectCode: section.subjectCode });
    for (const option of part.options ?? []) {
      for (const section of option.sections ?? [])
        for (const block of section.blocks ?? []) visit(block, { subjectCode: section.subjectCode, optionCode: option.code });
      for (const block of option.blocks ?? []) visit(block, { optionCode: option.code });
    }
  }
  return seen;
}
