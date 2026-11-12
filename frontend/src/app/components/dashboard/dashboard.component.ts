import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TaskService } from '../../services/task.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="max-width: 1000px; margin: 2rem auto; padding: 0 1rem;">
      <h2 style="font-size: 1.8rem; margin-bottom: 1.5rem; color: #1e293b;">Task Management Dashboard</h2>
      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1.25rem;">
        <div style="background:#fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.06); text-align: center;">
          <h3 style="color:#64748b; font-size: 0.95rem; text-transform: uppercase; margin: 0 0 0.5rem 0;">Total Tasks</h3>
          <span style="font-size: 2.2rem; font-weight: bold; color: #1e293b;">{{ metrics().total }}</span>
        </div>
        <div style="background:#fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.06); text-align: center;">
          <h3 style="color:#64748b; font-size: 0.95rem; text-transform: uppercase; margin: 0 0 0.5rem 0;">Todo</h3>
          <span style="font-size: 2.2rem; font-weight: bold; color: #f59e0b;">{{ metrics().todo }}</span>
        </div>
        <div style="background:#fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.06); text-align: center;">
          <h3 style="color:#64748b; font-size: 0.95rem; text-transform: uppercase; margin: 0 0 0.5rem 0;">In Progress</h3>
          <span style="font-size: 2.2rem; font-weight: bold; color: #3b82f6;">{{ metrics().inProgress }}</span>
        </div>
        <div style="background:#fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.06); text-align: center;">
          <h3 style="color:#64748b; font-size: 0.95rem; text-transform: uppercase; margin: 0 0 0.5rem 0;">Completed</h3>
          <span style="font-size: 2.2rem; font-weight: bold; color: #10b981;">{{ metrics().completed }}</span>
        </div>
        <div style="background:#fff; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.06); text-align: center;">
          <h3 style="color:#64748b; font-size: 0.95rem; text-transform: uppercase; margin: 0 0 0.5rem 0;">Cancelled</h3>
          <span style="font-size: 2.2rem; font-weight: bold; color: #ef4444;">{{ metrics().cancelled }}</span>
        </div>
      </div>
    </div>
  `
})
export class DashboardComponent implements OnInit {
  metrics = signal({ total: 0, todo: 0, inProgress: 0, completed: 0, cancelled: 0 });

  constructor(private taskService: TaskService) {}

  ngOnInit(): void {
    this.taskService.getTasks().subscribe({
      next: (tasks) => {
        this.metrics.set({
          total: tasks.length,
          todo: tasks.filter(t => t.status === 'TODO').length,
          inProgress: tasks.filter(t => t.status === 'IN_PROGRESS').length,
          completed: tasks.filter(t => t.status === 'COMPLETED').length,
          cancelled: tasks.filter(t => t.status === 'CANCELLED').length
        });
      },
      error: (err) => console.error('Error loading dashboard metrics:', err)
    });
  }
}