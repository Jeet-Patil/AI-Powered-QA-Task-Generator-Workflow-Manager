import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TaskService } from '../../services/task.service';
import { Task, TaskStatus } from '../../models/task.model';

@Component({
  selector: 'app-task-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <div class="card">
      <div class="header-row">
        <h2>Tasks</h2>
        <button class="btn btn-primary" routerLink="/tasks/new">+ Add Task</button>
      </div>

      <div class="search-box">
        <input 
          type="text" 
          placeholder="Search tasks..." 
          [ngModel]="searchTerm()"
          (ngModelChange)="searchTerm.set($event)"
          class="form-control" />
      </div>

      <table>
        <thead>
          <tr>
            <th>Title</th>
            <th>Priority</th>
            <th>Status</th>
            <th>Assignee</th>
            <th>Due Date</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          @for (task of filteredTasks(); track task.id) {
            <tr>
              <td><strong>{{ task.title }}</strong></td>
              <td><span class="badge" [ngClass]="'badge-' + task.priority.toLowerCase()">{{ task.priority }}</span></td>
              <td><span class="badge" [ngClass]="'badge-' + task.status.toLowerCase()">{{ task.status }}</span></td>
              <td>{{ task.assignee || 'Unassigned' }}</td>
              <td>{{ task.dueDate || 'No date' }}</td>
              <td class="actions">
                <button class="btn btn-sm btn-outline" [routerLink]="['/tasks', task.id]">View</button>
                <button class="btn btn-sm btn-outline" [routerLink]="['/tasks', task.id, 'edit']">Edit</button>
                <button class="btn btn-sm btn-danger" (click)="deleteTask(task)">Delete</button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6" style="text-align:center; padding: 2rem;">No tasks found.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    .header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
    .search-box { margin-bottom: 1.5rem; }
    .search-box input { width: 100%; max-width: 400px; padding: 0.5rem; border: 1px solid #cbd5e1; border-radius: 4px; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 0.75rem; border-bottom: 1px solid #e2e8f0; }
    th { background: #f8fafc; color: #475569; font-weight: 600; }
    .actions { display: flex; gap: 0.5rem; }
  `]
})
export class TaskListComponent implements OnInit {
  tasks = signal<Task[]>([]);
  searchTerm = signal<string>('');

  filteredTasks = computed(() => {
    const term = this.searchTerm().toLowerCase();
    return this.tasks().filter(t => 
      t.title.toLowerCase().includes(term) || 
      (t.assignee && t.assignee.toLowerCase().includes(term))
    );
  });

  constructor(private taskService: TaskService) {}

  ngOnInit() {
    this.loadTasks();
  }

  loadTasks() {
    this.taskService.getTasks().subscribe({
      next: (data) => this.tasks.set(data),
      error: (err) => console.error('Failed to load tasks', err)
    });
  }

  deleteTask(task: Task) {
    if (confirm(`Are you sure you want to delete "${task.title}"?`)) {
      this.taskService.deleteTask(task.id!).subscribe({
        next: () => this.loadTasks(),
        error: (err) => {
          const msg = err.error?.message || err.error?.error || err.statusText || 'Unable to delete task';
          alert(msg);
        }
      });
    }
  }
}