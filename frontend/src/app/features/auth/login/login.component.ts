import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { extractApiError } from '../../../shared/utils/api.util';

@Component({
  selector: 'app-login',
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
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(8)] })
  });

  hidePassword = true;
  loading = false;
  errorMessage = '';
  successMessage = '';

  ngOnInit(): void {
    if (this.auth.isAuthenticated) {
      void this.router.navigate(['/chat']);
      return;
    }

    if (this.route.snapshot.queryParamMap.get('reason') === 'expired') {
      this.errorMessage = 'Votre session a expiré. Veuillez vous reconnecter.';
    }

    if (this.route.snapshot.queryParamMap.get('registered') === '1') {
      this.successMessage = 'Votre compte a été créé. Vous pouvez vous connecter.';
    }
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

    if (control.hasError('minlength')) {
      return 'Le mot de passe doit contenir au moins 8 caractères.';
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

    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.loading = false;
        void this.router.navigateByUrl(this.returnUrl());
      },
      error: (error: unknown) => {
        this.loading = false;
        this.errorMessage = extractApiError(error);
      }
    });
  }

  private returnUrl(): string {
    const candidate = this.route.snapshot.queryParamMap.get('returnUrl');
    return candidate && candidate.startsWith('/') && !candidate.startsWith('//') ? candidate : '/chat';
  }
}
