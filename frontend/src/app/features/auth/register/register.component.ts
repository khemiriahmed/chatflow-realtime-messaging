import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { extractApiError } from '../../../shared/utils/api.util';
import { passwordsMatchValidator } from '../../../shared/utils/form.util';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegisterComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  readonly form = new FormGroup(
    {
      name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2), Validators.maxLength(100)] }),
      email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
      password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8), Validators.maxLength(72)] }),
      confirmPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] })
    },
    { validators: passwordsMatchValidator('password', 'confirmPassword') }
  );

  hidePassword = true;
  loading = false;
  errorMessage = '';

  ngOnInit(): void {
    if (this.auth.isAuthenticated) {
      void this.router.navigate(['/chat']);
    }
  }

  get nameError(): string {
    const control = this.form.controls.name;

    if (control.hasError('required')) {
      return 'Le nom est obligatoire.';
    }

    if (control.hasError('minlength') || control.hasError('maxlength')) {
      return 'Le nom doit contenir entre 2 et 100 caractères.';
    }

    return '';
  }

  get emailError(): string {
    const control = this.form.controls.email;

    if (control.hasError('required')) {
      return "L'adresse e-mail est obligatoire.";
    }

    if (control.hasError('email')) {
      return 'Saisissez une adresse e-mail valide.';
    }

    return '';
  }

  get passwordError(): string {
    const control = this.form.controls.password;

    if (control.hasError('required')) {
      return 'Le mot de passe est obligatoire.';
    }

    if (control.hasError('minlength') || control.hasError('maxlength')) {
      return 'Le mot de passe doit contenir entre 8 et 72 caractères.';
    }

    return '';
  }

  get confirmPasswordError(): string {
    if (this.form.hasError('passwordsMismatch')) {
      return 'Les mots de passe ne correspondent pas.';
    }

    if (this.form.controls.confirmPassword.hasError('required')) {
      return 'Confirmez votre mot de passe.';
    }

    return '';
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const { name, email, password } = this.form.getRawValue();

    this.auth.register({ name, email, password }).subscribe({
      next: () => {
        this.loading = false;
        this.notifications.success('Votre compte a été créé. Connectez-vous pour commencer.');
        void this.router.navigate(['/login'], { queryParams: { registered: '1' } });
      },
      error: (error: unknown) => {
        this.loading = false;
        this.errorMessage = extractApiError(error);
      }
    });
  }
}
