import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { debounceTime } from 'rxjs/operators';
import { AdminStatsDto, UserDto } from '../../../core/models';
import { AdminService } from '../../../core/services/admin.service';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { extractApiError } from '../../../shared/utils/api.util';

@Component({
  selector: 'app-admin-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatSlideToggleModule,
    AvatarComponent,
    EmptyStateComponent,
    LoadingStateComponent
  ],
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminPageComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly adminService = inject(AdminService);
  private readonly notifications = inject(NotificationService);

  readonly auth = inject(AuthService);
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly pageSize = 10;

  stats: AdminStatsDto | null = null;
  users: UserDto[] = [];
  page = 0;
  totalPages = 0;
  totalElements = 0;
  loadingStats = true;
  loadingUsers = true;
  errorMessage = '';

  constructor() {
    this.adminService.stats().subscribe({
      next: (stats) => {
        this.stats = stats;
        this.loadingStats = false;
      },
      error: (error: unknown) => {
        this.loadingStats = false;
        this.errorMessage = extractApiError(error);
      }
    });

    this.searchControl.valueChanges.pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.page = 0;
      this.loadUsers();
    });

    this.loadUsers();
  }

  get pageLabel(): string {
    return `Page ${this.page + 1} sur ${Math.max(this.totalPages, 1)}`;
  }

  get statsCards(): { label: string; value: number; icon: string; tone: string }[] {
    const stats = this.stats;

    if (!stats) {
      return [];
    }

    return [
      { label: 'Utilisateurs', value: stats.usersCount, icon: 'people', tone: 'primary' },
      { label: 'Comptes actifs', value: stats.activeUsersCount, icon: 'verified_user', tone: 'success' },
      { label: 'Conversations', value: stats.conversationsCount, icon: 'forum', tone: 'accent' },
      { label: 'Messages', value: stats.messagesCount, icon: 'chat', tone: 'primary' }
    ];
  }

  loadUsers(): void {
    this.loadingUsers = true;
    this.errorMessage = '';

    this.adminService.users(this.searchControl.value.trim(), this.page, this.pageSize).subscribe({
      next: (response) => {
        this.users = response.content;
        this.totalPages = response.totalPages;
        this.totalElements = response.totalElements;
        this.loadingUsers = false;
      },
      error: (error: unknown) => {
        this.loadingUsers = false;
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

  toggleStatus(user: UserDto, enabled: boolean): void {
    if (user.id === this.auth.currentUser?.id) {
      return;
    }

    this.adminService.setStatus(user.id, enabled).subscribe({
      next: (updated) => {
        this.users = this.users.map((item) => (item.id === updated.id ? updated : item));
        this.notifications.success(enabled ? 'Le compte a été activé.' : 'Le compte a été désactivé.');
      },
      error: (error: unknown) => {
        this.errorMessage = extractApiError(error);
        this.loadUsers();
      }
    });
  }

  changeRole(user: UserDto, role: string): void {
    if (user.role === role) {
      return;
    }

    this.adminService.changeRole(user.id, role).subscribe({
      next: (updated) => {
        this.users = this.users.map((item) => (item.id === updated.id ? updated : item));
        this.notifications.success('Le rôle de l’utilisateur a été mis à jour.');
      },
      error: (error: unknown) => {
        this.errorMessage = extractApiError(error);
        this.loadUsers();
      }
    });
  }

  trackByUser(_index: number, user: UserDto): number {
    return user.id;
  }
}
