# onboarding-frontend - Next.js Application

A modern Next.js application scaffolded with the NextFeature plugin (`next-feature`). This application includes TypeScript support, TailwindCSS styling, and integration with the @next-feature/client library for API communication.

## 🚀 Quick Start

### Prerequisites

- **Node.js** >= 18 (recommended 20+)
- **npm**, **pnpm**, or **yarn** package manager
- **Nx workspace** (part of NextFeature project)

### Development Server

Start the development server:

```bash
# From workspace root
npx nx serve onboarding-frontend

# Or from app directory
cd apps/onboarding-frontend
npm run dev
```

Visit [http://localhost:4200](http://localhost:4200) in your browser.

### Build for Production

```bash
npx nx build onboarding-frontend
```

### Run Tests

```bash
npx nx test onboarding-frontend
```

### Lint Code

```bash
npx nx lint onboarding-frontend
```

## 📁 Project Structure

This application is organized using the NextFeature pattern:

```
apps/onboarding-frontend/
├── src/
│   ├── app/                       # Next.js App Router
│   │   ├── layout.tsx             # Root layout component
│   │   ├── page.tsx               # Home page
│   │   └── [slug]/
│   │       └── page.tsx           # Dynamic routes
│   │
│   ├── lib/                       # Application library
│   │   ├── actions/               # Server actions (API calls, forms, DB ops)
│   │   ├── components/            # React components
│   │   ├── stores/                # Zustand state management hooks
│   │   ├── types/                 # TypeScript type definitions
│   │   ├── constants/             # Constants and enums
│   │   ├── utils/                 # Utility functions
│   │   ├── client/
│   │   │   └── config.ts          # API client configuration (auto-created)
│   │   └── axios/                 # HTTP client setup (if enabled)
│   │
│   ├── public/                    # Static assets
│   ├── styles/                    # Global styles
│   └── middleware.ts              # Next.js middleware (optional)
│
├── .env.example                   # Environment variables template
├── .env.local                     # Environment variables (gitignored)
├── next.config.js                 # Next.js configuration
├── tailwind.config.js             # TailwindCSS configuration
├── tsconfig.json                  # TypeScript configuration
├── jest.config.ts                 # Jest testing configuration
├── package.json                   # Dependencies and scripts
└── project.json                   # Nx project configuration
```

## 🏗 TypeScript Path Aliases

This application uses TypeScript path aliases for clean imports:

```typescript
// Instead of: import { Button } from '../../../../components/Button'
import { Button } from '@app/onboarding-frontend/components/Button'

// Works throughout the app
import { getUser } from '@app/onboarding-frontend/lib/actions'
import { useUserStore } from '@app/onboarding-frontend/lib/stores'
```

## 🎯 Using NextFeature Generators

The real power of NextFeature is code generation. Use these commands to scaffold code:

### 1. Create Components

```bash
# Basic component
npx nx g next-feature:component --name=UserProfile --projectName=onboarding-frontend

# Card component
npx nx g next-feature:component --name=UserCard --componentType=card --projectName=onboarding-frontend

# Modal component
npx nx g next-feature:component --name=DeleteConfirm --componentType=modal --projectName=onboarding-frontend

# Form component
npx nx g next-feature:component --name=LoginForm --componentType=form --projectName=onboarding-frontend

# Page component
npx nx g next-feature:component --name=Dashboard --componentType=page --projectName=onboarding-frontend

# Layout component
npx nx g next-feature:component --name=MainLayout --componentType=layout --projectName=onboarding-frontend
```

### 2. Create Server Actions

Server actions handle API calls, database operations, and form submissions:

```bash
# API action (with types and constants)
npx nx g next-feature:action --name=getUser --actionType=api --projectName=onboarding-frontend \
  --useTypes --useConstant

# Form submission action
npx nx g next-feature:action --name=loginUser --actionType=form --projectName=onboarding-frontend

# Database operation action
npx nx g next-feature:action --name=getUserFromDb --actionType=db --projectName=onboarding-frontend

# With custom client package
npx nx g next-feature:action --name=getUser --actionType=api --projectName=onboarding-frontend \
  --clientPackage="@myorg/api-client"
```

### 3. Create State Management

```bash
# Zustand store (default)
npx nx g next-feature:store --name=userStore --projectName=onboarding-frontend

# React Context provider
npx nx g next-feature:store --name=authStore --projectName=onboarding-frontend --useContext=true
```

### 4. Create Types and Constants

```bash
# TypeScript type definitions
npx nx g next-feature:data-type --name=User --projectName=onboarding-frontend

# Constants and enums
npx nx g next-feature:constant --name=userRoles --projectName=onboarding-frontend

# Utility functions
npx nx g next-feature:utility --name=formatters --projectName=onboarding-frontend
```

## 🌐 API Integration with @next-feature/client

When you create your first action with `--actionType=api`, the client-config generator runs automatically:

```bash
npx nx g next-feature:action --name=getUser --actionType=api --projectName=onboarding-frontend
```

This creates `src/lib/client/config.ts` with the ApiClient setup. Here's how to use it:

### In Server Actions

```typescript
// src/lib/actions/get-user.ts
'use server'

import apiClient from '../client/config'
import { ApiError, type ApiResponse } from '@next-feature/client'

export async function getUser(id: string): Promise<ApiResponse<User>> {
  try {
    const user = await apiClient.get<User>(`/users/${id}`)
    return { success: true, data: user }
  } catch (error) {
    const apiError = ApiError.of(error)
    return {
      success: false,
      message: apiError.message,
      error: apiError.problemDetail
    }
  }
}
```

### Error Handling with Validation

```typescript
'use server'

import { z } from 'zod'
import { ApiError, type ApiResponse } from '@next-feature/client'

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8)
})

export async function loginUser(
  formData: FormData
): Promise<ApiResponse<{ token: string }>> {
  const result = schema.safeParse({
    email: formData.get('email'),
    password: formData.get('password')
  })

  if (!result.success) {
    const apiError = ApiError.fromZodError(result.error)
    return {
      success: false,
      message: apiError.message,
      error: apiError.problemDetail
    }
  }

  try {
    const token = await apiClient.post<{ token: string }>('/auth/login', result.data)
    return { success: true, data: token }
  } catch (error) {
    const apiError = ApiError.of(error)
    return { success: false, message: apiError.message, error: apiError.problemDetail }
  }
}
```

## 📦 Creating Feature Libraries

For larger applications, organize code into feature libraries:

```bash
# Create a feature library
npx nx g next-feature:feature --name=users

# Create another feature
npx nx g next-feature:feature --name=products

# Generate code within a feature
npx nx g next-feature:action --name=getUser --actionType=api --projectName=users
npx nx g next-feature:component --name=UserCard --projectName=users
npx nx g next-feature:store --name=userStore --projectName=users
```

Then import from features:

```typescript
// Import from feature library
import { useUserStore } from '@feature/users'
import { UserCard } from '@feature/users'
```

## ⚙️ Configuration

### Environment Variables

Copy the template and add your configuration:

```bash
cp .env.example .env.local
```

Edit `.env.local`:

```env
# API Configuration
NEXT_PUBLIC_API_URL=http://localhost:3000
NEXT_PUBLIC_API_TIMEOUT=30000

# Authentication (if using NextAuth)
NEXTAUTH_URL=http://localhost:4200
NEXTAUTH_SECRET=your-secret-here
NEXT_PUBLIC_ROOT_DOMAIN=localhost:4200

# Database (if using Prisma)
DATABASE_URL=postgresql://user:password@localhost:5432/dbname
```

**Important:** Never commit `.env.local` to version control.

### TypeScript Configuration

Paths are configured in `tsconfig.json`:

```json
{
  "compilerOptions": {
    "paths": {
      "@app/onboarding-frontend/*": ["src/*"]
    }
  }
}
```

### TailwindCSS

Customize styling in `tailwind.config.js`:

```javascript
module.exports = {
  content: ["./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        primary: '#...',
        secondary: '#...'
      }
    }
  }
}
```

## 📝 Development Workflows

### Workflow 1: Building a Feature from Scratch

```bash
# 1. Create feature library
npx nx g next-feature:feature --name=users

# 2. Create types
npx nx g next-feature:data-type --name=User --projectName=users

# 3. Create constants
npx nx g next-feature:constant --name=userEndpoints --projectName=users

# 4. Create API action
npx nx g next-feature:action --name=getUsers --actionType=api --projectName=users

# 5. Create component
npx nx g next-feature:component --name=UserList --projectName=users

# 6. Create store for state
npx nx g next-feature:store --name=userStore --projectName=users

# 7. Create utilities if needed
npx nx g next-feature:utility --name=userHelpers --projectName=users
```

### Workflow 2: Form with Validation

```bash
# 1. Create form component
npx nx g next-feature:component --name=LoginForm --componentType=form --projectName=onboarding-frontend

# 2. Create form action with validation
npx nx g next-feature:action --name=login --actionType=form --projectName=onboarding-frontend

# 3. Use in component
# The action handles Zod validation and returns structured errors
```

### Workflow 3: Dashboard with Real-Time Updates

```bash
# 1. Create layout component
npx nx g next-feature:component --name=DashboardLayout --componentType=layout --projectName=onboarding-frontend

# 2. Create dashboard page
npx nx g next-feature:component --name=Dashboard --componentType=page --projectName=onboarding-frontend

# 3. Create data fetching action
npx nx g next-feature:action --name=getDashboardData --actionType=api --projectName=onboarding-frontend

# 4. Create store for dashboard state
npx nx g next-feature:store --name=dashboardStore --projectName=onboarding-frontend

# 5. Create card components for metrics
npx nx g next-feature:component --name=MetricCard --componentType=card --projectName=onboarding-frontend
```

## 🧪 Testing

### Run All Tests

```bash
npx nx test onboarding-frontend
```

### Run Specific Test File

```bash
npx nx test onboarding-frontend --testFile="src/lib/actions/get-user.spec.ts"
```

### Watch Mode

```bash
npx nx test onboarding-frontend -- --watch
```

### Coverage Report

```bash
npx nx test onboarding-frontend -- --coverage
```

Test files are created alongside source files with `.spec.ts` extension.

## 🔍 Linting and Formatting

### Lint Code

```bash
npx nx lint onboarding-frontend
```

### Fix Linting Issues

```bash
npx nx lint onboarding-frontend -- --fix
```

### Format Code

```bash
npx nx format:write
```

## 🐛 Troubleshooting

### Issue: Port 4200 already in use

**Solution:** Use a different port:

```bash
npx nx serve onboarding-frontend -- --port 3001
```

### Issue: TypeScript path aliases not working

**Solution:** Restart your IDE and clear cache:

```bash
npx nx reset
```

Then verify `tsconfig.json` paths configuration.

### Issue: Styles not applying

**Solution:** Ensure Tailwind is configured correctly:

```bash
cat tailwind.config.js | grep -A 3 'content:'
```

Should include:
```javascript
content: ["./src/**/*.{js,ts,jsx,tsx}"]
```

### Issue: API calls failing

**Solution:** Check `.env.local`:

```bash
cat .env.local | grep API_URL
```

Ensure the API URL matches your backend configuration.

### Issue: Generator command not found

**Solution:** The plugin might not be initialized. Run:

```bash
npx nx g next-feature:init
```

## 📚 Documentation

- **[NextFeature Plugin](../../README.md)** - Complete plugin overview
- **[Generator Onboarding](../ONBOARDING.md)** - Step-by-step onboarding guide
- **[Init Generator](../init/README.md)** - Plugin initialization
- **[Preset Generator](../preset/README.md)** - Initial setup
- **[Feature Generator](../project/feature/README.md)** - Feature libraries
- **[@next-feature/client](../../../../clients/client/README.md)** - API client documentation
- **[Nx Documentation](https://nx.dev)** - Monorepo framework docs
- **[Next.js Documentation](https://nextjs.org/docs)** - React framework docs

## 🛠 Common Commands Reference

```bash
# Development
npx nx serve onboarding-frontend              # Start dev server
npx nx build onboarding-frontend              # Build for production

# Testing & Quality
npx nx test onboarding-frontend               # Run tests
npx nx lint onboarding-frontend               # Lint code
npx nx format:write                   # Format all code

# Generation (from workspace root)
npx nx g next-feature:component --name=Button --projectName=onboarding-frontend
npx nx g next-feature:action --name=getUser --actionType=api --projectName=onboarding-frontend
npx nx g next-feature:store --name=userStore --projectName=onboarding-frontend
npx nx g next-feature:feature --name=users

# Workspace utilities
npx nx list onboarding-frontend               # Show project details
npx nx graph                          # Show dependency graph
npx nx reset                          # Clear Nx cache
```

## 📖 Getting Help

### View Generator Help

```bash
npx nx g next-feature:component --help
npx nx g next-feature:action --help
npx nx g next-feature:feature --help
```

### Check Nx Status

```bash
npx nx status
```

### View Project Configuration

```bash
cat project.json                      # This project's config
cat tsconfig.json                     # TypeScript config
cat next.config.js                    # Next.js config
```

## 🎓 Learning Path

1. **Start here** - This README
2. **Create your first component** - `npx nx g next-feature:component --name=Button --projectName=onboarding-frontend`
3. **Create an API action** - `npx nx g next-feature:action --name=getUser --actionType=api --projectName=onboarding-frontend`
4. **Create a feature library** - `npx nx g next-feature:feature --name=users`
5. **Generate code in features** - Use generators with `--projectName=users`

## 📝 Additional Resources

- **[Monorepo Best Practices](https://nx.dev/concepts/monorepo-best-practices)**
- **[Next.js App Router](https://nextjs.org/docs/app)**
- **[Server Components](https://nextjs.org/docs/app/building-your-application/rendering/server-components)**
- **[TypeScript Best Practices](https://www.typescriptlang.org/docs/handbook/)**

---

**Ready to start building?** Run your first generator:

```bash
npx nx g next-feature:component --name=Welcome --projectName=onboarding-frontend
```

Or create a feature library:

```bash
npx nx g next-feature:feature --name=users
```

Happy coding! 🚀
