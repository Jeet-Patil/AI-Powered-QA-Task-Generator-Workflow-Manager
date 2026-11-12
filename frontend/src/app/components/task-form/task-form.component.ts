import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TaskService } from '../../services/task.service';

@Component({
  selector: 'app-task-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <div style="max-width: 600px; margin: 2rem auto; padding: 0 1rem;">
      <h2 style="color: #1e293b; margin-bottom: 1.5rem;">{{ isEdit ? 'Edit Task' : 'Create Task' }}</h2>
      <form [formGroup]="taskForm" (ngSubmit)="onSubmit()" style="background:#fff; padding: 2rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.06);">
        <div style="margin-bottom: 1.25rem;">
          <label style="display:block; margin-bottom: 0.5rem; font-weight: 600; color: #334155;">Title *</label>
          <input type="text" formControlName="title" style="width: 100%; padding: 0.65rem; border: 1px solid #cbd5e1; border-radius: 6px; box-sizing: border-box;" />
          <small *ngIf="taskForm.get('title')?.invalid && taskForm.get('title')?.touched" style="color: #ef4444; display: block; margin-top: 0.25rem;">Title is required.</small>
        </div>

        <div style="margin-bottom: 1.25rem;">
          <label style="display:block; margin-bottom: 0.5rem; font-weight: 600; color: #334155;">Description</label>
          <textarea formControlName="description" rows="4" style="width: 100%; padding: 0.65rem; border: 1px solid #cbd5e1; border-radius: 6px; box-sizing: border-box;"></textarea>
        </div>

        <div style="margin-bottom: 1.25rem;">
          <label style="display:block; margin-bottom: 0.5rem; font-weight: 600; color: #334155;">Priority *</label>
          <select formControlName="priority" style="width: 100%; padding: 0.65rem; border: 1px solid #cbd5e1; border-radius: 6px; box-sizing: border-box;">
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
            <option value="CRITICAL">CRITICAL</option>
          </select>
        </div>

        <div style="margin-bottom: 1.25rem;">
          <label style="display:block; margin-bottom: 0.5rem; font-weight: 600; color: #334155;">Assignee</label>
          <input type="text" formControlName="assignee" placeholder="e.g. John" style="width: 100%; padding: 0.65rem; border: 1px solid #cbd5e1; border-radius: 6px; box-sizing: border-box;" />
        </div>

        <div style="margin-bottom: 1.5rem;">
          <label style="display:block; margin-bottom: 0.5rem; font-weight: 600; color: #334155;">Due Date</label>
          <input type="date" formControlName="dueDate" style="width: 100%; padding: 0.65rem; border: 1px solid #cbd5e1; border-radius: 6px; box-sizing: border-box;" />
          <small *ngIf="taskForm.get('dueDate')?.hasError('pastDate')" style="color: #ef4444; display: block; margin-top: 0.25rem;">Due date cannot be in the past.</small>
        </div>

        <div style="display:flex; gap: 1rem;">
          <button type="submit" [disabled]="taskForm.invalid" style="background: #2563eb; color: #fff; padding: 0.65rem 1.5rem; border: none; border-radius: 6px; cursor: pointer; font-weight: 600;">Save</button>
          <button type="button" (click)="router.navigate(['/tasks'])" style="background: #64748b; color: #fff; padding: 0.65rem 1.5rem; border: none; border-radius: 6px; cursor: pointer; font-weight: 600;">Cancel</button>
        </div>
      </form>
    </div>
  `
})
export class TaskFormComponent implements OnInit {
  taskForm!: FormGroup;
  get form(): FormGroup { return this.taskForm; }
  isEdit = false;
  taskId?: number;

  constructor(
    private fb: FormBuilder,
    private taskService: TaskService,
    public router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.taskForm = this.fb.group({
      title: ['', Validators.required],
      description: [''],
      priority: ['HIGH', Validators.required],
      assignee: [''],
      dueDate: ['', [this.futureDateValidator]]
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.taskId = +id;
      this.taskService.getTask(this.taskId).subscribe(task => {
        this.taskForm.patchValue({
          title: task.title,
          description: task.description,
          priority: task.priority,
          assignee: task.assignee,
          dueDate: task.dueDate
        });
      });
    }
  }

  futureDateValidator(control: any) {
    if (!control.value) return null;
    const [year, month, day] = control.value.split('-').map(Number);
    const selectedDate = new Date(year, month - 1, day);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return selectedDate < today ? { pastDate: true } : null;
  }

  onSubmit(): void {
    if (this.taskForm.invalid) return;

    if (this.isEdit && this.taskId) {
      this.taskService.updateTask(this.taskId, this.taskForm.value).subscribe({
        next: () => this.router.navigate(['/tasks']),
        error: (err) => alert(err.error?.error || 'Update failed')
      });
    } else {
      this.taskService.createTask(this.taskForm.value).subscribe({
        next: () => this.router.navigate(['/tasks']),
        error: (err) => alert(err.error?.error || 'Creation failed')
      });
    }
  }
}