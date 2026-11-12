import { Navigate, type RouteObject } from 'react-router';
import { notFound, pageElement, pages } from '@/pageRegistry';
import { AppShell } from '@/shell/AppShell';

export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      ...pages.map((page) => ({ path: page.path, element: pageElement(page) })),
      { path: notFound.path, element: pageElement(notFound) },
    ],
  },
];
