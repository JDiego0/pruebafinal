import { createBrowserRouter } from 'react-router-dom'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <div>Home Layout Placeholder</div>,
    children: [
      {
        index: true,
        element: <div>Conversations Placeholder</div>,
      },
      {
        path: 'profile',
        element: <div>Profile Placeholder</div>,
      }
    ]
  },
  {
    path: '/login',
    element: <div>Login Placeholder</div>,
  }
])
