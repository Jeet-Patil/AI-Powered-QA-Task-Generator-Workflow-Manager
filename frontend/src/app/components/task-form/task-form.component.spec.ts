import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TaskFormComponent } from './task-form.component';
import { TaskService } from '../../services/task.service';

describe('TaskFormComponent Unit Tests', () => {
  let component: TaskFormComponent;
  let fixture: ComponentFixture<TaskFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TaskFormComponent, ReactiveFormsModule],
      providers: [
        TaskService,
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TaskFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('1. should initialize the form with default controls', () => {
    expect(component.form).toBeDefined();
    expect(component.form.get('title')).toBeDefined();
    expect(component.form.get('priority')).toBeDefined();
    expect(component.form.get('dueDate')).toBeDefined();
  });

  it('2. Rule 1: title should be mandatory', () => {
    const titleControl = component.form.get('title');
    titleControl?.setValue('');
    expect(titleControl?.invalid).toBe(true);
    expect(component.form.invalid).toBe(true);
  });

  it('3. Rule 2: priority should be mandatory', () => {
    const priorityControl = component.form.get('priority');
    priorityControl?.setValue(null);
    expect(priorityControl?.invalid).toBe(true);
  });

  it('4. Rule 5: reject a past due date', () => {
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const dateValue = [yesterday.getFullYear(), String(yesterday.getMonth() + 1).padStart(2, '0'),
      String(yesterday.getDate()).padStart(2, '0')].join('-');
    const dateControl = component.form.get('dueDate');
    dateControl?.setValue(dateValue);
    expect(dateControl?.hasError('pastDate')).toBe(true);
  });

  it('5. Rule 5: allow today as a due date', () => {
    const today = new Date();
    const dateValue = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'),
      String(today.getDate()).padStart(2, '0')].join('-');
    const dateControl = component.form.get('dueDate');
    dateControl?.setValue(dateValue);
    expect(dateControl?.hasError('pastDate')).toBe(false);
  });

  it('6. should mark form as valid when all required fields are filled', () => {
    component.form.patchValue({
      title: 'Valid PRD Task',
      priority: 'HIGH',
      dueDate: '2026-12-31'
    });
    expect(component.form.valid).toBe(true);
  });
});