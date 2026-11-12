export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface Task {
  id?: number;
  title: string;
  description?: string;
  priority: Priority;
  status: TaskStatus;
  assignee?: string;
  dueDate?: string;
  createdAt?: string;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  priority: Priority;
  assignee?: string;
  dueDate?: string;
}

export interface UpdateTaskRequest {
  title: string;
  description?: string;
  priority: Priority;
  assignee?: string;
  dueDate?: string;
}

export interface StatusUpdateRequest {
  status: TaskStatus;
}

export type TestType = 'POSITIVE' | 'NEGATIVE' | 'BOUNDARY' | 'EDGE';
export type TestCaseStatus = 'DRAFT' | 'APPROVED' | 'REJECTED';
export type ExecutionResult = 'NOT_RUN' | 'PASS' | 'FAIL' | 'BLOCKED';

export interface TestCase {
  id: number;
  title: string;
  description?: string;
  preconditions?: string;
  steps: string;
  expectedResult: string;
  testType: TestType;
  status: TestCaseStatus;
  executionResult: ExecutionResult;
  actualResult?: string;
  executionNotes?: string;
  executedAt?: string;
}

export interface ExecuteTestCaseRequest {
  result: ExecutionResult;
  actualResult?: string;
  executionNotes?: string;
}