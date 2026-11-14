import { z } from "zod";

export const CLASS_CODE_REGEX = /^[A-Za-z0-9][A-Za-z0-9._-]{1,29}$/;

export interface ClassFormValidationMessages {
  codeRequired: string;
  codeInvalid: string;
  nameRequired: string;
  nameMax: string;
  descriptionMax: string;
}

export function createClassFormSchema(messages: ClassFormValidationMessages) {
  return z.object({
    code: z
      .string()
      .transform((value) => value.trim())
      .refine((value) => value.length > 0, messages.codeRequired)
      .refine((value) => CLASS_CODE_REGEX.test(value), messages.codeInvalid),
    name: z
      .string()
      .transform((value) => value.trim())
      .refine((value) => value.length > 0, messages.nameRequired)
      .refine((value) => value.length <= 200, messages.nameMax),
    description: z
      .string()
      .transform((value) => value.trim())
      .refine((value) => value.length <= 1000, messages.descriptionMax),
    ownerUserId: z.string(),
    status: z.enum(["ACTIVE", "INACTIVE"]),
  });
}

export interface ClassFormValues {
  code: string;
  name: string;
  description: string;
  ownerUserId: string;
  status: "ACTIVE" | "INACTIVE";
}

export interface TransferMemberValidationMessages {
  targetClassRequired: string;
  reasonMax: string;
}

export function createTransferMemberSchema(messages: TransferMemberValidationMessages) {
  return z.object({
    targetClassId: z.string().min(1, messages.targetClassRequired),
    reason: z
      .string()
      .transform((value) => value.trim())
      .refine((value) => value.length <= 500, messages.reasonMax),
  });
}

export interface TransferMemberFormValues {
  targetClassId: string;
  reason: string;
}
