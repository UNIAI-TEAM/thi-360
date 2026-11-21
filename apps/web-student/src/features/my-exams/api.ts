import type { components } from "@thi360/api-client";
import { createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type MyExamCardData = components["schemas"]["MyExamCard"];
export type MyExamsResponse = components["schemas"]["MyExamsResponse"];
export type MyExamAction = MyExamCardData["actions"][number];

export const MY_EXAMS_QUERY_KEY = ["me", "exams"] as const;
export type MyExamsLoader = () => Promise<MyExamsResponse>;

export async function loadMyExams(): Promise<MyExamsResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/exams");
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}
