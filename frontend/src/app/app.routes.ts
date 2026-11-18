import { Routes } from '@angular/router';
import { adminGuard } from './core/guards/admin.guard';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Connexion — ChatFlow',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent)
  },
  {
    path: 'register',
    title: 'Inscription — ChatFlow',
    loadComponent: () => import('./features/auth/register/register.component').then((m) => m.RegisterComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layouts/main-layout/main-layout.component').then((m) => m.MainLayoutComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'chat' },
      {
        path: 'chat',
        title: 'Messagerie — ChatFlow',
        loadComponent: () => import('./features/chat/chat-page/chat-page.component').then((m) => m.ChatPageComponent)
      },
      {
        path: 'chat/:conversationId',
        title: 'Conversation — ChatFlow',
        loadComponent: () => import('./features/chat/chat-page/chat-page.component').then((m) => m.ChatPageComponent)
      },
      {
        path: 'contacts',
        title: 'Contacts — ChatFlow',
        loadComponent: () => import('./features/contacts/contacts-page/contacts-page.component').then((m) => m.ContactsPageComponent)
      },
      {
        path: 'groups',
        title: 'Groupes — ChatFlow',
        loadComponent: () => import('./features/groups/groups-page/groups-page.component').then((m) => m.GroupsPageComponent)
      },
      {
        path: 'profile',
        title: 'Mon profil — ChatFlow',
        loadComponent: () => import('./features/profile/profile-page/profile-page.component').then((m) => m.ProfilePageComponent)
      },
      {
        path: 'admin',
        canActivate: [adminGuard],
        title: 'Administration — ChatFlow',
        loadComponent: () => import('./features/admin/admin-page/admin-page.component').then((m) => m.AdminPageComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'chat' }
];
