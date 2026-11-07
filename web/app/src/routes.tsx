import { Navigate, type RouteObject } from 'react-router';
import { PageError } from '@/components/PageError';
import { notFound, pageElement, pages } from '@/pageRegistry';
import { AppShell } from '@/shell/AppShell';

export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    // The shell itself failing (rare): still not React Router's bare default.
    errorElement: <PageError />,
    children: [
      { index: true, element: <Navigate to="/home" replace /> },
      // A page that fails shows its error inside the shell: the sidebar and player stay.
      ...pages.map((page) => ({ path: page.path, element: pageElement(page), errorElement: <PageError /> })),
      { path: notFound.path, element: pageElement(notFound), errorElement: <PageError /> },
    ],
  },
];
