import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TaskService } from './task.service';
import { Task, CreateTaskRequest, StatusUpdateRequest } from '../models/task.model';

describe('TaskService Unit Tests', () => {
  let service: TaskService;
  let httpMock: HttpTestingController;

  const mockTasks: Task[] = [
    { id: 1, title: 'Test 1', priority: 'HIGH', status: 'TODO' },
    { id: 2, title: 'Test 2', priority: 'LOW', status: 'IN_PROGRESS' }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        TaskService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(TaskService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('1. should fetch all tasks (GET /api/tasks)', () => {
    service.getTasks().subscribe((tasks: Task[]) => {
      expect(tasks.length).toBe(2);
      expect(tasks[0].title).toBe('Test 1');
    });
    const req = httpMock.expectOne('http://localhost:8081/api/tasks');
    expect(req.request.method).toBe('GET');
    req.flush(mockTasks);
  });

  it('2. should fetch task by ID (GET /api/tasks/1)', () => {
    service.getTask(1).subscribe((task: Task) => {
      expect(task.id).toBe(1);
    });
    const req = httpMock.expectOne('http://localhost:8081/api/tasks/1');
    expect(req.request.method).toBe('GET');
    req.flush(mockTasks[0]);
  });

  it('3. should create a task (POST /api/tasks)', () => {
    const newReq: CreateTaskRequest = { title: 'New Task', priority: 'CRITICAL' };
    service.createTask(newReq).subscribe((task: Task) => {
      expect(task.title).toBe('New Task');
    });
    const req = httpMock.expectOne('http://localhost:8081/api/tasks');
    expect(req.request.method).toBe('POST');
    req.flush({ ...newReq, id: 3, status: 'TODO' });
  });

  it('4. should update task status (PATCH /api/tasks/1/status)', () => {
    const statusPayload: StatusUpdateRequest = { status: 'IN_PROGRESS' };
    service.changeStatus(1, statusPayload).subscribe((task: Task) => {
      expect(task.status).toBe('IN_PROGRESS');
    });
    const req = httpMock.expectOne('http://localhost:8081/api/tasks/1/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(statusPayload);
    req.flush({ ...mockTasks[0], status: 'IN_PROGRESS' });
  });

  it('5. should delete task (DELETE /api/tasks/1)', () => {
    service.deleteTask(1).subscribe((res: void) => {
      expect(res).toBeNull();
    });
    const req = httpMock.expectOne('http://localhost:8081/api/tasks/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should send the selected provider and custom prompt for generation', () => {
    service.generateTestCases(42, 'Include accessibility cases', 'groq').subscribe((cases) => {
      expect(cases).toEqual([]);
    });
    const req = httpMock.expectOne('http://localhost:8081/api/testcases/generate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      taskId: 42,
      customPrompt: 'Include accessibility cases',
      provider: 'groq'
    });
    req.flush([]);
  });
});