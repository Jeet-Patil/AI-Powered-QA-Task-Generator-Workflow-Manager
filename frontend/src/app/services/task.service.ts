import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Task, CreateTaskRequest, UpdateTaskRequest, StatusUpdateRequest, TestCase, TestCaseStatus, ExecuteTestCaseRequest } from '../models/task.model';

@Injectable({
  providedIn: 'root'
})
export class TaskService {
  private readonly apiUrl = 'http://localhost:8081/api/tasks';
  private readonly tcUrl = 'http://localhost:8081/api/testcases';

  constructor(private http: HttpClient) {}

  getTasks(): Observable<Task[]> { return this.http.get<Task[]>(this.apiUrl); }
  getTask(id: number): Observable<Task> { return this.http.get<Task>(`${this.apiUrl}/${id}`); }
  createTask(task: CreateTaskRequest): Observable<Task> { return this.http.post<Task>(this.apiUrl, task); }
  updateTask(id: number, task: UpdateTaskRequest): Observable<Task> { return this.http.put<Task>(`${this.apiUrl}/${id}`, task); }
  deleteTask(id: number): Observable<void> { return this.http.delete<void>(`${this.apiUrl}/${id}`); }
  changeStatus(id: number, status: StatusUpdateRequest): Observable<Task> { return this.http.patch<Task>(`${this.apiUrl}/${id}/status`, status); }

  getTestCases(taskId: number): Observable<TestCase[]> { return this.http.get<TestCase[]>(`${this.tcUrl}/task/${taskId}`); }
  generateTestCases(taskId: number, customPrompt?: string, provider?: string): Observable<TestCase[]> { return this.http.post<TestCase[]>(`${this.tcUrl}/generate`, { taskId, customPrompt, provider }); }
  reviewTestCase(testCaseId: number, status: TestCaseStatus): Observable<TestCase> { return this.http.patch<TestCase>(`${this.tcUrl}/${testCaseId}/review`, { status }); }
  executeTestCase(testCaseId: number, req: ExecuteTestCaseRequest): Observable<TestCase> { return this.http.post<TestCase>(`${this.tcUrl}/${testCaseId}/execute`, req); }
}