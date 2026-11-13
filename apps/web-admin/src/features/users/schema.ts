import { isPasswordStrong } from "@thi360/ui";
import { z } from "zod";

export const USERNAME_NORMALIZED_REGEX = /^[a-z0-9][a-z0-9._@-]{3,29}$/;

export const DEFAULT_RESERVED_USERNAMES: Record<string, true> = {
  admin: true,
  administrator: true,
  root: true,
  system: true,
  support: true,
  platform: true,
  thi360: true,
  null: true,
  undefined: true,
};

export interface CreateUserValidationMessages {
  usernameRequired: string;
  usernameInvalid: string;
  usernameReserved: string;
  passwordWeak: string;
  passwordSameAsUsername: string;
  fullNameRequired: string;
  fullNameMax: string;
  emailInvalid: string;
  emailMax: string;
  phoneMax: string;
}

export function createCreateUserSchema(messages: CreateUserValidationMessages) {
  return z
    .object({
      username: z
        .string()
        .transform((value) => value.trim())
        .refine((value) => value.length > 0, messages.usernameRequired)
        .refine(
          (value) => USERNAME_NORMALIZED_REGEX.test(value.toLowerCase()),
          messages.usernameInvalid,
        )
        .refine(
          (value) => !Object.hasOwn(DEFAULT_RESERVED_USERNAMES, value.toLowerCase()),
          messages.usernameReserved,
        ),
      password: z
        .string()
        .refine((value) => isPasswordStrong(value), messages.passwordWeak),
      fullName: z
        .string()
        .transform((value) => value.trim())
        .refine((value) => value.length > 0, messages.fullNameRequired)
        .refine((value) => value.length <= 200, messages.fullNameMax),
      email: z
        .string()
        .transform((value) => value.trim())
        .refine((value) => value.length <= 255, messages.emailMax)
        .refine(
          (value) => value.length === 0 || z.email().safeParse(value).success,
          messages.emailInvalid,
        ),
      phone: z
        .string()
        .transform((value) => value.trim())
        .refine((value) => value.length <= 30, messages.phoneMax),
      roleId: z.string(),
      mustChangePassword: z.boolean(),
    })
    .refine(
      (values) =>
        !values.username ||
        values.password.toLowerCase() !== values.username.trim().toLowerCase(),
      {
        message: messages.passwordSameAsUsername,
        path: ["password"],
      },
    );
}

export interface CreateUserFormValues {
  username: string;
  password: string;
  fullName: string;
  email: string;
  phone: string;
  roleId: string;
  mustChangePassword: boolean;
}

export interface UpdateUserValidationMessages {
  fullNameRequired: string;
  fullNameMax: string;
  emailInvalid: string;
  emailMax: string;
  phoneMax: string;
}

export function createUpdateUserSchema(messages: UpdateUserValidationMessages) {
  return z.object({
    fullName: z
      .string()
      .transform((value) => value.trim())
      .refine((value) => value.length > 0, messages.fullNameRequired)
      .refine((value) => value.length <= 200, messages.fullNameMax),
    email: z
      .string()
      .transform((value) => value.trim())
      .refine((value) => value.length <= 255, messages.emailMax)
      .refine(
        (value) => value.length === 0 || z.email().safeParse(value).success,
        messages.emailInvalid,
      ),
    phone: z
      .string()
      .transform((value) => value.trim())
      .refine((value) => value.length <= 30, messages.phoneMax),
  });
}

export interface UpdateUserFormValues {
  fullName: string;
  email: string;
  phone: string;
}

export interface ResetPasswordValidationMessages {
  passwordWeak: string;
  passwordSameAsUsername: string;
  reasonMax: string;
}

export function createResetPasswordSchema(
  messages: ResetPasswordValidationMessages,
  username: string,
) {
  return z.object({
    password: z
      .string()
      .refine((value) => isPasswordStrong(value), messages.passwordWeak)
      .refine(
        (value) => !username || value.toLowerCase() !== username.trim().toLowerCase(),
        messages.passwordSameAsUsername,
      ),
    reason: z
      .string()
      .transform((value) => value.trim())
      .refine((value) => value.length <= 500, messages.reasonMax),
    mustChangePassword: z.boolean(),
  });
}

export interface ResetPasswordFormValues {
  password: string;
  reason: string;
  mustChangePassword: boolean;
}
