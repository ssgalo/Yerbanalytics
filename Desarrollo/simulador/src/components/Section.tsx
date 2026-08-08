import type { ReactNode } from 'react';

interface Props {
  title: string;
  hint?: string;
  children: ReactNode;
}

/** Card with a title and one line of context. All the layout system this tool needs. */
export function Section({ title, hint, children }: Props) {
  return (
    <section className="card">
      <h2 className="sectionTitle">{title}</h2>
      {hint && <p className="sectionHint">{hint}</p>}
      {children}
    </section>
  );
}
