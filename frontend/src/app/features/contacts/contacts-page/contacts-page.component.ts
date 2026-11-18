import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { UserDto } from '../../../core/models';
import { AuthService } from '../../../core/services/auth.service';
import { ConversationService } from '../../../core/services/conversation.service';
import { NotificationService } from '../../../core/services/notification.service';
import { UserService } from '../../../core/services/user.service';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { extractApiError } from '../../../shared/utils/api.util';

@Component({
  selector: 'app-contacts-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    AvatarComponent,
    EmptyStateComponent,
    LoadingStateComponent
  ],
  templateUrl: './contacts-page.component.html',
  styleUrl: './contacts-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ContactsPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly conversationService = inject(ConversationService);
  private readonly notifications = inject(NotificationService);

  readonly auth = inject(AuthService);
  readonly searchControl = new FormControl(this.route.snapshot.queryParamMap.get('q') ?? '', { nonNullable: true });
  readonly pageSize = 12;

  users: UserDto[] = [];
  page = 0;
  totalPages = 0;
  totalElements = 0;
  loading = true;
  errorMessage = '';
  readonly startingConversations = new Set<number>();

  constructor() {
    this.searchControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.page = 0;
        this.loadUsers();
      });

    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const query = params.get('q') ?? '';
      if (query !== this.searchControl.value) {
        this.searchControl.setValue(query);
      }
    });

    this.loadUsers();
  }

  get pageLabel(): string {
    return `Page ${this.page + 1} sur ${Math.max(this.totalPages, 1)}`;
  }

  loadUsers(): void {
    this.loading = true;
    this.errorMessage = '';

    this.userService.search(this.searchControl.value.trim(), this.page, this.pageSize).subscribe({
      next: (response) => {
        this.users = response.content.filter((user) => user.id !== this.auth.currentUser?.id);
        this.totalPages = response.totalPages;
        this.totalElements = response.totalElements;
        this.loading = false;
      },
      error: (error: unknown) => {
        this.loading = false;
        this.errorMessage = extractApiError(error);
      }
    });
  }

  changePage(delta: number): void {
    const nextPage = this.page + delta;

    if (nextPage < 0 || (this.totalPages > 0 && nextPage >= this.totalPages)) {
      return;
    }

    this.page = nextPage;
    this.loadUsers();
  }

  startConversation(user: UserDto): void {
    if (this.startingConversations.has(user.id)) {
      return;
    }

    this.startingConversations.add(user.id);

    this.conversationService
      .startPrivateConversation(user.id)
      .pipe(finalize(() => this.startingConversations.delete(user.id)))
      .subscribe({
        next: (conversation) => void this.router.navigate(['/chat', conversation.id]),
        error: (error: unknown) => this.notifications.error(extractApiError(error))
      });
  }

  isStarting(user: UserDto): boolean {
    return this.startingConversations.has(user.id);
  }

  trackByUser(_index: number, user: UserDto): number {
    return user.id;
  }
}
