export interface Tag {
  id: string;
  name: string;
  color: string | null;
  usageCount: number;
}

export interface UpsertTagRequest {
  name: string;
  color?: string | null;
}
