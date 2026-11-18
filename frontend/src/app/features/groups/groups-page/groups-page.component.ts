import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { ConversationDto, UserDto } from '../../../core/models';
import { AuthService } from '../../../core/services/auth.service';
import { ConversationService } from '../../../core/services/conversation.service';
import { NotificationService } from '../../../core/services/notification.service';
import { UserService } from '../../../core/services/user.service';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { extractApiError } from '../../../shared/utils/api.util';

@Component({
  selector: 'app-groups-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    AvatarComponent,
    EmptyStateComponent
  ],
  templateUrl: './groups-page.component.html',
  styleUrl: './groups-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GroupsPageComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly userService = inject(UserService);
  private readonly conversationService = inject(ConversationService);
  private readonly notifications = inject(NotificationService);

  readonly auth = inject(AuthService);
  readonly groups$ = this.conversationService.conversations$;

  readonly createForm = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2), Validators.maxLength(100)] }),
    memberIds: new FormControl<number[]>([], { nonNullable: true, validators: [Validators.required] })
  });
  readonly addMemberControl = new FormControl<number | null>(null);

  selectedGroup: ConversationDto | null = null;
  users: UserDto[] = [];
  creating = false;
  addingMember = false;
  errorMessage = '';

  ngOnInit(): void {
    this.conversationService.refreshConversations().subscribe({
      error: (error: unknown) => (this.errorMessage = extractApiError(error))
    });

    this.conversationService.conversations$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((conversations) => {
        if (this.selectedGroup !== null) {
          this.selectedGroup = conversations.find((item) => item.id === this.selectedGroup?.id) ?? null;
        }
      });

    this.userService.search('', 0, 50).subscribe({
      next: (response) => (this.users = response.content.filter((user) => user.id !== this.auth.currentUser?.id)),
      error: (error: unknown) => (this.errorMessage = extractApiError(error))
    });
  }

  get titleError(): string {
    const control = this.createForm.controls.title;

    if (control.hasError('required')) {
      return 'Le nom du groupe est obligatoire.';
    }

    if (control.hasError('minlength') || control.hasError('maxlength')) {
      return 'Le nom doit contenir entre 2 et 100 caractères.';
    }

    return '';
  }

  selectGroup(group: ConversationDto): void {
    this.selectedGroup = group;
    this.errorMessage = '';
    this.addMemberControl.setValue(null);
  }

  trackByGroup(_index: number, group: ConversationDto): number {
    return group.id;
  }

  trackByUser(_index: number, user: UserDto): number {
    return user.id;
  }

  roleLabel(group: ConversationDto, member: UserDto): string {
    const role = group.memberRoles?.[member.id];

    if (role === 'OWNER') {
      return 'Propriétaire';
    }

    if (role === 'ADMIN') {
      return 'Administrateur';
    }

    return 'Membre';
  }

  isMember(group: ConversationDto, userId: number): boolean {
    return group.members.some((member) => member.id === userId);
  }

  canManage(group: ConversationDto): boolean {
    const role = group.memberRoles?.[this.auth.currentUser?.id ?? -1];
    return this.auth.isAdmin || role === 'OWNER' || role === 'ADMIN';
  }

  currentUserRole(group: ConversationDto): string {
    const currentUser = this.auth.currentUser;

    if (!currentUser) {
      return 'Membre';
    }

    return this.roleLabel(group, currentUser);
  }

  createGroup(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const { title, memberIds } = this.createForm.getRawValue();
    const trimmedTitle = title.trim();

    if (trimmedTitle.length < 2) {
      return;
    }

    this.creating = true;
    this.errorMessage = '';

    this.conversationService.createGroup(trimmedTitle, memberIds).subscribe({
      next: (group) => {
        this.creating = false;
        this.selectedGroup = group;
        this.createForm.reset({ title: '', memberIds: [] });
        this.notifications.success('Le groupe a été créé.');
      },
      error: (error: unknown) => {
        this.creating = false;
        this.errorMessage = extractApiError(error);
      }
    });
  }

  addMember(): void {
    const userId = this.addMemberControl.value;

    if (this.selectedGroup === null || userId === null) {
      return;
    }

    this.addingMember = true;
    this.errorMessage = '';

    this.conversationService.addMember(this.selectedGroup.id, userId).subscribe({
      next: (group) => {
        this.addingMember = false;
        this.selectedGroup = group;
        this.addMemberControl.setValue(null);
        this.notifications.success('Le membre a été ajouté au groupe.');
      },
      error: (error: unknown) => {
        this.addingMember = false;
        this.errorMessage = extractApiError(error);
      }
    });
  }

  removeMember(member: UserDto): void {
    const group = this.selectedGroup;

    if (group === null || group.members[0]?.id === member.id) {
      return;
    }

    this.conversationService.removeMember(group.id, member.id).subscribe({
      next: () => {
        this.selectedGroup = {
          ...group,
          members: group.members.filter((item) => item.id !== member.id)
        };
        this.notifications.success('Le membre a été retiré du groupe.');
      },
      error: (error: unknown) => (this.errorMessage = extractApiError(error))
    });
  }

  leaveGroup(): void {
    const group = this.selectedGroup;

    if (group === null) {
      return;
    }

    this.conversationService.leave(group.id).subscribe({
      next: () => {
        this.selectedGroup = null;
        this.notifications.success('Vous avez quitté le groupe.');
        void this.router.navigate(['/chat']);
      },
      error: (error: unknown) => (this.errorMessage = extractApiError(error))
    });
  }
}
