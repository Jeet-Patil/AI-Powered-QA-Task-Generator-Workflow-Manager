import { ActivatedRoute, Router } from '@angular/router';
import { vi } from 'vitest';
import { TaskService } from '../../services/task.service';
import { TaskDetailsComponent } from './task-details.component';

describe('TaskDetailsComponent', () => {
  it('does not execute a test case when execution notes are canceled', () => {
    const executeTestCase = vi.fn();
    const taskService = { executeTestCase } as unknown as TaskService;
    const route = { snapshot: { paramMap: { get: () => null } } } as unknown as ActivatedRoute;
    const component = new TaskDetailsComponent(route, {} as Router, taskService);
    vi.spyOn(window, 'prompt').mockReturnValue(null);

    component.executeTestCase(10, 'PASS');

    expect(executeTestCase).not.toHaveBeenCalled();
    vi.restoreAllMocks();
  });
});