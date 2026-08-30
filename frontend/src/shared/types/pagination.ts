// Mirrors backend com.glv.gsysportal.dto.response.PageResponse (Phase 8-G -
// the first Backend-paginated List screen in this codebase). page is
// 0-indexed, matching MUI TablePagination's own convention directly.

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
