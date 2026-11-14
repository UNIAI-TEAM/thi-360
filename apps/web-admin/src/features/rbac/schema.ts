import { z } from "zod";

export const roleFormSchema = z.object({
  code: z.string().trim().min(1, "codeRequired").max(64, "codeMaxLength"),
  name: z.string().trim().min(1, "nameRequired").max(120, "nameMaxLength"),
  description: z.string().max(500, "descriptionMaxLength").optional(),
  access: z.enum(["MANAGE", "EXAM"]),
  status: z.enum(["ACTIVE", "INACTIVE"]),
});

export type RoleFormValues = z.infer<typeof roleFormSchema>;

export const directPermissionFormSchema = z.object({
  reason: z.string().trim().min(1, "directReasonRequired").max(500),
});

export type DirectPermissionFormValues = z.infer<typeof directPermissionFormSchema>;
