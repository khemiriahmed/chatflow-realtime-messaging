import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { truncateText } from '../../shared/utils/conversation.util';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.show(message, ['success-snackbar']);
  }

  error(message: string): void {
    this.show(message, ['error-snackbar']);
  }

  info(message: string): void {
    this.show(message, []);
  }

  messageNotification(senderName: string, content: string, conversationId: number | null): void {
    const text = truncateText(`${senderName} : ${content}`, 160);
    this.info(text);
    this.browserNotification('ChatFlow', text, conversationId);
  }

  requestPermission(): void {
    if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'default') {
      void Notification.requestPermission();
    }
  }

  private show(message: string, panelClass: string[]): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 4000,
      horizontalPosition: 'right',
      verticalPosition: 'top',
      panelClass
    });
  }

  private browserNotification(title: string, body: string, conversationId: number | null): void {
    if (
      typeof window === 'undefined' ||
      !('Notification' in window) ||
      Notification.permission !== 'granted' ||
      !document.hidden
    ) {
      return;
    }

    const notification = new Notification(title, {
      body,
      tag: conversationId === null ? 'chatflow' : `chatflow-${conversationId}`
    });
    notification.onclick = () => {
      window.focus();
      notification.close();
    };
  }
}
