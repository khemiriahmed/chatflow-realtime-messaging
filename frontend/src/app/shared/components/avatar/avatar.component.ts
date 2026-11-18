import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { InitialsPipe } from '../../pipes/initials.pipe';

@Component({
  selector: 'app-avatar',
  standalone: true,
  imports: [CommonModule, InitialsPipe],
  templateUrl: './avatar.component.html',
  styleUrl: './avatar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AvatarComponent {
  @Input({ required: true }) name = '';
  @Input() size: 'sm' | 'md' | 'lg' = 'md';
  @Input() online: boolean | null = null;
  @Input() showPresence = true;

  get ariaLabel(): string {
    const state = this.online === null ? '' : this.online ? ', en ligne' : ', hors ligne';
    return `Avatar de ${this.name}${state}`;
  }
}
