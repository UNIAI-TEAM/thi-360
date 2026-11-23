"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  FormField,
  Input,
  Select,
} from "@thi360/ui";
import { useTranslations } from "next-intl";
import { useMemo } from "react";
import { Controller, useForm } from "react-hook-form";
import { z } from "zod";

export const BRANCH_NAME_MAX = 100;

export interface BranchDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Môn chọn được cho nhánh (môn của phần chung, rồi các môn khác của danh mục). */
  subjects: ReadonlyArray<{ code: string; name: string }>;
  defaultSubjectCode: string;
  /** Số câu nhánh nhận: số câu của phần trừ phần chung. */
  questionCount: number;
  onAdd: (branch: { name: string; subjectCode: string }) => void;
}

export function BranchDialog({ open, onOpenChange, subjects, defaultSubjectCode, questionCount, onAdd }: BranchDialogProps) {
  const t = useTranslations("exams.combinations.branchDialog");
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent size="md" closeLabel={t("cancel")}>
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description", { count: questionCount })}</DialogDescription>
        </DialogHeader>
        {open && (
          <BranchForm
            subjects={subjects}
            defaultSubjectCode={defaultSubjectCode}
            onCancel={() => onOpenChange(false)}
            onAdd={(branch) => {
              onAdd(branch);
              onOpenChange(false);
            }}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function BranchForm({
  subjects,
  defaultSubjectCode,
  onCancel,
  onAdd,
}: Pick<BranchDialogProps, "subjects" | "defaultSubjectCode" | "onAdd"> & { onCancel: () => void }) {
  const t = useTranslations("exams.combinations.branchDialog");
  const schema = useMemo(
    () =>
      z.object({
        name: z
          .string()
          .trim()
          .min(1, t("nameRequired"))
          .max(BRANCH_NAME_MAX, t("nameMax", { max: BRANCH_NAME_MAX })),
        subjectCode: z.string().min(1, t("subjectRequired")),
      }),
    [t],
  );
  type Values = z.infer<typeof schema>;
  const {
    control,
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    mode: "onTouched",
    defaultValues: { name: "", subjectCode: defaultSubjectCode },
    shouldFocusError: true,
  });

  return (
    <form onSubmit={(event) => void handleSubmit(onAdd)(event)} noValidate className="flex flex-col gap-4">
      <FormField label={t("name")} required hint={t("nameHint")} error={errors.name?.message}>
        {(controlProps) => <Input {...controlProps} autoComplete="off" maxLength={BRANCH_NAME_MAX} {...register("name")} />}
      </FormField>
      <Controller
        control={control}
        name="subjectCode"
        render={({ field }) => (
          <FormField label={t("subject")} required error={errors.subjectCode?.message}>
            {(controlProps) => (
              <Select
                {...controlProps}
                value={field.value}
                onValueChange={field.onChange}
                options={subjects.map((subject) => ({ value: subject.code, label: subject.name }))}
              />
            )}
          </FormField>
        )}
      />
      <DialogFooter>
        <Button type="button" variant="secondary" onClick={onCancel}>
          {t("cancel")}
        </Button>
        <Button type="submit">{t("submit")}</Button>
      </DialogFooter>
    </form>
  );
}
