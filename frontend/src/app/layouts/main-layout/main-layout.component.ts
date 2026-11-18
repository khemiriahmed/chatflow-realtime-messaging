import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map, shareReplay, tap } from 'rxjs/operators';
import { NotificationDto } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { WebSocketService } from '../../core/services/websocket.service';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatBadgeModule,
    MatButtonModule,
    MatDividerModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatSidenavModule,
    MatToolbarModule,
    MatTooltipModule,
    AvatarComponent
  ],
  templateUrl: './main-layout.component.html',
  styleUrl: './main-layout.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MainLayoutComponent {
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private isHandset = false;

  readonly auth = inject(AuthService);
  readonly ws = inject(WebSocketService);
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly isHandset$ = this.breakpointObserver.observe([Breakpoints.XSmall, Breakpoints.Small]).pipe(
    map((result) => result.matches),
    tap((matches) => (this.isHandset = matches)),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  notifications: NotificationDto[] = [];

  @ViewChild(MatSidenav) sidenav?: MatSidenav;

  constructor() {
    this.ws.connect();

    this.ws.notifications$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((notification) => this.addNotification(notification));

    this.ws.messages$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((message) =>
        this.addNotification({
          type: 'MESSAGE',
          message: `${message.senderName} : ${message.content}`,
          conversationId: message.conversationId,
          createdAt: message.createdAt
        })
      );

    this.auth.sessionExpired$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => void this.router.navigate(['/login'], { queryParams: { reason: 'expired' } }));
  }

  get isBackVisible(): boolean {
    return this.isHandset && /^\/chat\/\d+$/.test(this.router.url);
  }

  get notificationCount(): number {
    return this.notifications.length;
  }

  closeSidenavOnHandset(): void {
    if (this.isHandset) {
      void this.sidenav?.close();
    }
  }

  onSearch(): void {
    const query = this.searchControl.value.trim();
    void this.router.navigate(['/contacts'], { queryParams: query ? { q: query } : {} });
  }

  openNotification(notification: NotificationDto): void {
    if (notification.conversationId !== null) {
      void this.router.navigate(['/chat', notification.conversationId]);
    }
  }

  logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigate(['/login']));
  }

  private addNotification(notification: NotificationDto): void {
    this.notifications = [notification, ...this.notifications].slice(0, 10);
  }
}
