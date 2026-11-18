import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractApiError } from '../../../shared/utils/api.util';
import { passwordsMatchValidator } from '../../../shared/utils/form.util';
import { AvatarComponent } from '../../../shared/components/avatar/avatar.component';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    AvatarComponent
  ],
  templateUrl: './profile-page.component.html',
  styleUrl: './profile-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProfilePageComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  readonly profileForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2), Validators.maxLength(100)] })
  });

  readonly passwordForm = new FormGroup(
    {
      currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      newPassword: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8), Validators.maxLength(72)] }),
      confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] })
    },
    { validators: passwordsMatchValidator('newPassword', 'confirmPassword') }
  );

  savingProfile = false;
  savingPassword = false;
  profileError = '';
  passwordError = '';
  passwordSuccess = '';

  ngOnInit(): void {
    const user = this.auth.currentUser;
    if (user) {
      this.profileForm.controls.name.setValue(user.name);
    }
  }

  get profileNameError(): string {
    const control = this.profileForm.controls.name;

    if (control.hasError('required')) {
      return 'Le nom est obligatoire.';
    }

    if (control.hasError('minlength') || control.hasError('maxlength')) {
      return 'Le nom doit contenir entre 2 et 100 caractères.';
    }

    return '';
  }

  get newPasswordError(): string {
    const control = this.passwordForm.controls.newPassword;

    if (control.hasError('required')) {
      return 'Le nouveau mot de passe est obligatoire.';
    }

    if (control.hasError('minlength') || control.hasError('maxlength')) {
      return 'Le mot de passe doit contenir entre 8 et 72 caractères.';
    }

    return '';
  }

  get confirmPasswordError(): string {
    if (this.passwordForm.hasError('passwordsMismatch')) {
      return 'Les mots de passe ne correspondent pas.';
    }

    if (this.passwordForm.controls.confirmPassword.hasError('required')) {
      return 'Confirmez le nouveau mot de passe.';
    }

    return '';
  }

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }

    this.savingProfile = true;
    this.profileError = '';

    this.auth.updateProfile({ name: this.profileForm.controls.name.value.trim() }).subscribe({
      next: () => {
        this.savingProfile = false;
        this.notifications.success('Votre profil a été mis à jour.');
      },
      error: (error: unknown) => {
        this.savingProfile = false;
        this.profileError = extractApiError(error);
      }
    });
  }

  changePassword(): void {
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      return;
    }

    this.savingPassword = true;
    this.passwordError = '';
    this.passwordSuccess = '';

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();

    this.auth.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.savingPassword = false;
        this.passwordSuccess = 'Votre mot de passe a été modifié.';
        this.passwordForm.reset();
        this.notifications.success('Votre mot de passe a été modifié.');
      },
      error: (error: unknown) => {
        this.savingPassword = false;
        this.passwordError = extractApiError(error);
      }
    });
  }

  enableNotifications(): void {
    this.notifications.requestPermission();
    this.notifications.info('Les notifications du navigateur ont été activées si votre navigateur les autorise.');
  }
}
