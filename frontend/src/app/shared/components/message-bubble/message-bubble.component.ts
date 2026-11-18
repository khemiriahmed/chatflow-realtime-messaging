import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MessageDto } from '../../../core/models';
import { TimePipe } from '../../pipes/time.pipe';

@Component({
  selector: 'app-message-bubble',
  standalone: true,
  imports: [CommonModule, MatIconModule, TimePipe],
  templateUrl: './message-bubble.component.html',
  styleUrl: './message-bubble.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MessageBubbleComponent {
  @Input({ required: true }) message!: MessageDto;
  @Input() sent = false;
  @Input() showAuthor = false;

  get statusIcon(): string {
    switch (this.message.status) {
      case 'PENDING':
        return 'schedule';
      case 'READ':
      case 'DELIVERED':
        return 'done_all';
      default:
        return 'done';
    }
  }

  get statusLabel(): string {
    switch (this.message.status) {
      case 'PENDING':
        return 'Envoi en cours';
      case 'READ':
        return 'Lu';
      case 'DELIVERED':
        return 'Distribué';
      default:
        return 'Envoyé';
    }
  }
}
