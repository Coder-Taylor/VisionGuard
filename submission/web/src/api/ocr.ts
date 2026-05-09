import client from './client';
import type { ApiResponse, PaginatedData, OcrRecordData, OcrResultData } from '../types';

export async function listOcrRecords(elderId: string, page = 1, pageSize = 20): Promise<PaginatedData<OcrRecordData>> {
  const res = await client.get<ApiResponse<PaginatedData<OcrRecordData>>>('/api/v1/ocr/records', {
    params: { elderId, page, pageSize },
  });
  return res.data.data;
}

export async function getOcrResult(taskId: string): Promise<OcrResultData> {
  const res = await client.get<ApiResponse<OcrResultData>>(`/api/v1/ocr/result/${taskId}`);
  return res.data.data;
}
