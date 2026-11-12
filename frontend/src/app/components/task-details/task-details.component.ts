import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TaskService } from '../../services/task.service';
import { Task, TestCase, TestCaseStatus, ExecutionResult } from '../../models/task.model';

@Component({
  selector: 'app-task-details',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div style="max-width: 900px; margin: 2rem auto; padding: 0 1rem; font-family: system-ui, -apple-system, sans-serif;">
      @if (task(); as t) {
        <!-- Task Header & Details -->
        <div style="background:#fff; padding: 2rem; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.08), 0 2px 4px -2px rgba(0,0,0,0.04); margin-bottom: 2rem; border: 1px solid #e2e8f0;">
          <div style="display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap: 1rem;">
            <div>
              <span style="font-size: 0.85rem; color: #64748b; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em;">Task #{{ t.id }}</span>
              <h2 style="margin: 0.25rem 0 0.5rem; color: #0f172a; font-size: 1.6rem;">{{ t.title }}</h2>
            </div>
            <div style="display:flex; gap: 0.5rem; align-items: center;">
              <span [style.background]="getPriorityBg(t.priority)" [style.color]="getPriorityColor(t.priority)" style="padding: 0.3rem 0.8rem; border-radius: 9999px; font-weight: 700; font-size: 0.85rem;">
                {{ t.priority }}
              </span>
              <span style="background: #f1f5f9; color: #334155; padding: 0.3rem 0.8rem; border-radius: 9999px; font-weight: 700; font-size: 0.85rem;">
                {{ t.status }}
              </span>
            </div>
          </div>

          <hr style="border:0; border-top: 1px solid #e2e8f0; margin: 1.25rem 0;" />

          <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; color: #334155;">
            <div>
              <span style="font-size: 0.85rem; color: #64748b; display: block;">Assignee</span>
              <strong style="color: #1e293b;">{{ t.assignee || 'Unassigned' }}</strong>
            </div>
            <div>
              <span style="font-size: 0.85rem; color: #64748b; display: block;">Due Date</span>
              <strong style="color: #1e293b;">{{ t.dueDate || 'No due date' }}</strong>
            </div>
          </div>

          <div style="margin-bottom: 1.5rem;">
            <span style="font-size: 0.85rem; color: #64748b; display: block; margin-bottom: 0.4rem;">Description</span>
            <div style="background: #f8fafc; padding: 1rem; border-radius: 8px; border: 1px solid #e2e8f0; color: #334155; white-space: pre-line; line-height: 1.6;">
              {{ t.description || 'No description provided.' }}
            </div>
          </div>

          <!-- Task Action Buttons -->
          <div style="display:flex; gap: 0.75rem; flex-wrap: wrap;">
            <!-- Start Task if TODO -->
            @if (t.status === 'TODO') {
              <button (click)="updateStatus('IN_PROGRESS')" style="background: #0284c7; color: #fff; border:none; padding: 0.6rem 1.2rem; border-radius: 6px; cursor: pointer; font-weight: 600;">Start Task</button>
            }

            <!-- Rule 3: Only IN_PROGRESS can be completed -->
            <button (click)="updateStatus('COMPLETED')" [disabled]="t.status !== 'IN_PROGRESS'" [style.opacity]="t.status !== 'IN_PROGRESS' ? 0.4 : 1" style="background: #16a34a; color: #fff; border:none; padding: 0.6rem 1.2rem; border-radius: 6px; cursor: pointer; font-weight: 600;">Mark Completed</button>

            <!-- Rule 4: Completed cannot be cancelled -->
            <button (click)="updateStatus('CANCELLED')" [disabled]="t.status === 'COMPLETED'" [style.opacity]="t.status === 'COMPLETED' ? 0.4 : 1" style="background: #d97706; color: #fff; border:none; padding: 0.6rem 1.2rem; border-radius: 6px; cursor: pointer; font-weight: 600;">Cancel</button>

            <!-- Rule 6: Completed cannot be deleted -->
            <button (click)="deleteTask()" [disabled]="t.status === 'COMPLETED'" [style.opacity]="t.status === 'COMPLETED' ? 0.4 : 1" style="background: #dc2626; color: #fff; border:none; padding: 0.6rem 1.2rem; border-radius: 6px; cursor: pointer; font-weight: 600;">Delete</button>

            <button (click)="router.navigate(['/tasks'])" style="background: #64748b; color: #fff; border:none; padding: 0.6rem 1.2rem; border-radius: 6px; cursor: pointer; font-weight: 600;">Back</button>
          </div>
        </div>

        <!-- AI QA Test Cases Management Section -->
        <div style="background:#fff; padding: 2rem; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.08), 0 2px 4px -2px rgba(0,0,0,0.04); border: 1px solid #e2e8f0;">
          <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap: 1rem; margin-bottom: 1.5rem;">
            <div>
              <h3 style="margin: 0; color: #0f172a; font-size: 1.35rem; display:flex; align-items:center; gap: 0.5rem;">
                🧪 AI QA Test Cases
                <span style="font-size: 0.85rem; background: #e0e7ff; color: #3730a3; padding: 0.2rem 0.6rem; border-radius: 9999px; font-weight: 600;">{{ testCases().length }} Total</span>
              </h3>
              <p style="margin: 0.25rem 0 0; color: #64748b; font-size: 0.9rem;">Automated QA testing suite and quality verification metrics.</p>
            </div>
            <div style="display:flex; gap: 0.5rem; align-items:center;">
              <select [ngModel]="provider()" (ngModelChange)="provider.set($event)"
                      title="Which LLM generates the test cases"
                      style="padding: 0.6rem 0.7rem; border-radius: 6px; border: 1px solid #cbd5e1; background: #fff; color: #334155; font-weight: 600; cursor: pointer;">
                <option value="auto">Auto (Ollama → Groq → Gemini)</option>
                <option value="ollama">Local Ollama</option>
                <option value="groq">Groq</option>
                <option value="gemini">Google Gemini</option>
              </select>
              <button (click)="generateTestCases()" [disabled]="generating()" style="background: #4f46e5; color: #fff; border:none; padding: 0.65rem 1.25rem; border-radius: 6px; cursor: pointer; font-weight: 600; display:flex; align-items:center; gap: 0.5rem;">
                @if (generating()) {
                  <span>Generating AI Tests...</span>
                } @else {
                  <span>⚡ Generate AI Test Cases</span>
                }
              </button>
            </div>
          </div>

          @if (testCases().length === 0) {
            <div style="text-align: center; padding: 3rem 1.5rem; background: #f8fafc; border-radius: 8px; border: 2px dashed #cbd5e1;">
              <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">🤖</div>
              <h4 style="margin: 0 0 0.5rem; color: #334155;">No Test Cases Generated Yet</h4>
              <p style="margin: 0; color: #64748b; font-size: 0.9rem;">Click "Generate AI Test Cases" above to auto-generate test suites covering all edge cases.</p>
            </div>
          } @else {
            <div style="display: flex; flex-direction: column; gap: 1rem;">
              @for (tc of testCases(); track tc.id) {
                <div style="border: 1px solid #e2e8f0; border-radius: 8px; padding: 1.25rem; background: #fafafa; transition: box-shadow 0.2s;" [style.border-left]="getTestTypeBorder(tc.testType)">
                  <div style="display:flex; justify-content:space-between; align-items:flex-start; flex-wrap:wrap; gap: 0.75rem; margin-bottom: 0.75rem;">
                    <div>
                      <div style="display:flex; align-items:center; gap: 0.5rem; flex-wrap:wrap; margin-bottom: 0.35rem;">
                        <span [style.background]="getTestTypeBg(tc.testType)" [style.color]="getTestTypeColor(tc.testType)" style="padding: 0.2rem 0.6rem; border-radius: 4px; font-weight: 700; font-size: 0.75rem;">
                          {{ tc.testType }}
                        </span>
                        <span [style.background]="getStatusBg(tc.status)" [style.color]="getStatusColor(tc.status)" style="padding: 0.2rem 0.6rem; border-radius: 4px; font-weight: 700; font-size: 0.75rem;">
                          {{ tc.status }}
                        </span>
                        <span [style.background]="getResultBg(tc.executionResult)" [style.color]="getResultColor(tc.executionResult)" style="padding: 0.2rem 0.6rem; border-radius: 4px; font-weight: 700; font-size: 0.75rem;">
                          {{ tc.executionResult }}
                        </span>
                      </div>
                      <h4 style="margin: 0; color: #1e293b; font-size: 1.05rem;">{{ tc.title }}</h4>
                    </div>

                    <!-- Test Case Action Buttons -->
                    <div style="display:flex; gap: 0.4rem; flex-wrap:wrap; align-items:center;">
                      <!-- Review Buttons -->
                      @if (tc.status === 'DRAFT' || tc.status === 'REJECTED') {
                        <button (click)="reviewTestCase(tc.id, 'APPROVED')" style="background: #16a34a; color: #fff; border:none; padding: 0.4rem 0.8rem; border-radius: 4px; cursor: pointer; font-size: 0.85rem; font-weight: 600;">Approve</button>
                      }
                      @if (tc.status === 'DRAFT' || tc.status === 'APPROVED') {
                        <button (click)="reviewTestCase(tc.id, 'REJECTED')" style="background: #dc2626; color: #fff; border:none; padding: 0.4rem 0.8rem; border-radius: 4px; cursor: pointer; font-size: 0.85rem; font-weight: 600;">Reject</button>
                      }

                      <!-- Execution Buttons (only enabled if APPROVED) -->
                      <button (click)="executeTestCase(tc.id, 'PASS')" [disabled]="tc.status !== 'APPROVED'" [style.opacity]="tc.status !== 'APPROVED' ? 0.35 : 1" style="background: #059669; color: #fff; border:none; padding: 0.4rem 0.8rem; border-radius: 4px; cursor: pointer; font-size: 0.85rem; font-weight: 600;">Pass</button>
                      <button (click)="executeTestCase(tc.id, 'FAIL')" [disabled]="tc.status !== 'APPROVED'" [style.opacity]="tc.status !== 'APPROVED' ? 0.35 : 1" style="background: #b91c1c; color: #fff; border:none; padding: 0.4rem 0.8rem; border-radius: 4px; cursor: pointer; font-size: 0.85rem; font-weight: 600;">Fail</button>
                      <button (click)="executeTestCase(tc.id, 'BLOCKED')" [disabled]="tc.status !== 'APPROVED'" [style.opacity]="tc.status !== 'APPROVED' ? 0.35 : 1" style="background: #d97706; color: #fff; border:none; padding: 0.4rem 0.8rem; border-radius: 4px; cursor: pointer; font-size: 0.85rem; font-weight: 600;">Block</button>
                    </div>
                  </div>

                  <div style="font-size: 0.9rem; color: #475569; line-height: 1.5; display: flex; flex-direction: column; gap: 0.5rem; background: #fff; padding: 0.85rem; border-radius: 6px; border: 1px solid #f1f5f9;">
                    @if (tc.description) {
                      <div><strong>Description:</strong> {{ tc.description }}</div>
                    }
                    @if (tc.preconditions) {
                      <div><strong>Preconditions:</strong> {{ tc.preconditions }}</div>
                    }
                    <div><strong>Steps:</strong> <span style="white-space: pre-line;">{{ tc.steps }}</span></div>
                    <div><strong>Expected Result:</strong> {{ tc.expectedResult }}</div>
                    @if (tc.executionNotes) {
                      <div style="color: #0f766e; background: #f0fdfa; padding: 0.4rem 0.6rem; border-radius: 4px;">
                        <strong>Execution Notes:</strong> {{ tc.executionNotes }}
                      </div>
                    }
                  </div>
                </div>
              }
            </div>
          }
        </div>
      } @else {
        <div style="text-align: center; padding: 3rem; color: #64748b;">Loading task details...</div>
      }
    </div>
  `
})
export class TaskDetailsComponent implements OnInit {
  task = signal<Task | null>(null);
  testCases = signal<TestCase[]>([]);
  generating = signal<boolean>(false);
  /** 'auto' | 'ollama' | 'groq' | 'gemini' */
  provider = signal<string>('auto');

  constructor(
    private route: ActivatedRoute,
    public router: Router,
    private taskService: TaskService
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.loadTask(+id);
      this.loadTestCases(+id);
    }
  }

  loadTask(id: number): void {
    this.taskService.getTask(id).subscribe({
      next: (t) => this.task.set(t),
      error: (err) => alert(err.error?.error || 'Task not found')
    });
  }

  loadTestCases(taskId: number): void {
    this.taskService.getTestCases(taskId).subscribe({
      next: (cases) => this.testCases.set(cases),
      error: (err) => console.error('Failed to load test cases', err)
    });
  }

  generateTestCases(): void {
    const t = this.task();
    if (!t?.id) return;
    this.generating.set(true);
    this.taskService.generateTestCases(t.id, undefined, this.provider()).subscribe({
      next: (cases) => {
        this.testCases.update((existing) => [...existing, ...cases]);
        this.generating.set(false);
      },
      error: (err) => {
        this.generating.set(false);
        alert(err.error?.error || 'Failed to generate test cases');
      }
    });
  }

  reviewTestCase(testCaseId: number, status: TestCaseStatus): void {
    this.taskService.reviewTestCase(testCaseId, status).subscribe({
      next: (updated) => {
        this.testCases.update((cases) =>
          cases.map((c) => (c.id === updated.id ? updated : c))
        );
      },
      error: (err) => alert(err.error?.error || 'Failed to update test case review status')
    });
  }

  executeTestCase(testCaseId: number, result: ExecutionResult): void {
    const enteredNotes = window.prompt('Enter execution notes:');
    if (enteredNotes === null) return;
    const notes = enteredNotes.trim() || `Executed with result: ${result}`;
    this.taskService.executeTestCase(testCaseId, {
      result,
      executionNotes: notes,
      actualResult: notes
    }).subscribe({
      next: (updated) => {
        this.testCases.update((cases) =>
          cases.map((c) => (c.id === updated.id ? updated : c))
        );
      },
      error: (err) => alert(err.error?.error || 'Failed to execute test case')
    });
  }

  updateStatus(newStatus: 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'): void {
    const current = this.task();
    if (!current?.id) return;
    this.taskService.changeStatus(current.id, { status: newStatus }).subscribe({
      next: (updated) => this.task.set(updated),
      error: (err) => alert(err.error?.error || 'Status update rejected by business rules')
    });
  }

  deleteTask(): void {
    const current = this.task();
    if (!current?.id) return;
    if (confirm('Are you sure you want to delete this task?')) {
      this.taskService.deleteTask(current.id).subscribe({
        next: () => this.router.navigate(['/tasks']),
        error: (err) => alert(err.error?.error || 'Delete failed')
      });
    }
  }

  getPriorityBg(priority: string): string {
    switch (priority) {
      case 'CRITICAL': return '#fee2e2';
      case 'HIGH': return '#ffedd5';
      case 'MEDIUM': return '#fef3c7';
      default: return '#dcfce7';
    }
  }

  getPriorityColor(priority: string): string {
    switch (priority) {
      case 'CRITICAL': return '#dc2626';
      case 'HIGH': return '#c2410c';
      case 'MEDIUM': return '#b45309';
      default: return '#15803d';
    }
  }

  getTestTypeBorder(type: string): string {
    switch (type) {
      case 'POSITIVE': return '4px solid #16a34a';
      case 'NEGATIVE': return '4px solid #dc2626';
      case 'BOUNDARY': return '4px solid #d97706';
      case 'EDGE': return '4px solid #7c3aed';
      default: return '4px solid #64748b';
    }
  }

  getTestTypeBg(type: string): string {
    switch (type) {
      case 'POSITIVE': return '#dcfce7';
      case 'NEGATIVE': return '#fee2e2';
      case 'BOUNDARY': return '#fef3c7';
      case 'EDGE': return '#ede9fe';
      default: return '#f1f5f9';
    }
  }

  getTestTypeColor(type: string): string {
    switch (type) {
      case 'POSITIVE': return '#15803d';
      case 'NEGATIVE': return '#b91c1c';
      case 'BOUNDARY': return '#b45309';
      case 'EDGE': return '#6d28d9';
      default: return '#334155';
    }
  }

  getStatusBg(status: string): string {
    switch (status) {
      case 'APPROVED': return '#dcfce7';
      case 'REJECTED': return '#fee2e2';
      default: return '#f1f5f9';
    }
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'APPROVED': return '#15803d';
      case 'REJECTED': return '#b91c1c';
      default: return '#475569';
    }
  }

  getResultBg(result: string): string {
    switch (result) {
      case 'PASS': return '#dcfce7';
      case 'FAIL': return '#fee2e2';
      case 'BLOCKED': return '#fef3c7';
      default: return '#f1f5f9';
    }
  }

  getResultColor(result: string): string {
    switch (result) {
      case 'PASS': return '#15803d';
      case 'FAIL': return '#b91c1c';
      case 'BLOCKED': return '#b45309';
      default: return '#64748b';
    }
  }
}