const STACK = [
  {
    category: 'Frontend',
    color: 'blue',
    items: ['React.js', 'Tailwind CSS', 'Axios', 'React Flow', 'Vite'],
  },
  {
    category: 'Backend',
    color: 'indigo',
    items: ['Java 21', 'Spring Boot 3', 'Spring Security', 'REST APIs'],
  },
  {
    category: 'AI Layer',
    color: 'purple',
    items: ['Google Gemini', 'OpenAI GPT', 'Prompt Engineering'],
  },
  {
    category: 'Database',
    color: 'green',
    items: ['PostgreSQL', 'Spring Data JPA', 'Hibernate'],
  },
  {
    category: 'External APIs',
    color: 'orange',
    items: ['GitHub REST API', 'JavaParser AST', 'Repository Metadata'],
  },
  {
    category: 'DevOps',
    color: 'gray',
    items: ['Docker', 'Docker Compose', 'JWT Auth'],
  },
]

const colorMap = {
  blue: 'bg-blue-50 border-blue-200 text-blue-700',
  indigo: 'bg-indigo-50 border-indigo-200 text-indigo-700',
  purple: 'bg-purple-50 border-purple-200 text-purple-700',
  green: 'bg-green-50 border-green-200 text-green-700',
  orange: 'bg-orange-50 border-orange-200 text-orange-700',
  gray: 'bg-gray-50 border-gray-200 text-gray-700',
}

const headerMap = {
  blue: 'text-blue-600',
  indigo: 'text-indigo-600',
  purple: 'text-purple-600',
  green: 'text-green-600',
  orange: 'text-orange-600',
  gray: 'text-gray-600',
}

export default function TechStackCard() {
  return (
    <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
      {STACK.map(({ category, color, items }) => (
        <div key={category} className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm">
          <h3 className={`text-xs font-bold uppercase tracking-wider mb-3 ${headerMap[color]}`}>
            {category}
          </h3>
          <div className="flex flex-wrap gap-1.5">
            {items.map((item) => (
              <span
                key={item}
                className={`text-xs border rounded-full px-2.5 py-0.5 font-medium ${colorMap[color]}`}
              >
                {item}
              </span>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
