"use client";

import { useActionState } from "react";
import { runAction, runStaffAction, type FormState } from "@/app/admin/actions";

const INITIAL: FormState = { ok: true, message: "" };

/** `staff` actions change the database directly; the rest run on the game tick. */
export function ActionForm({
  type,
  submit,
  danger = false,
  staff = false,
  confirm,
  hidden = {},
  className = "",
  children,
}: {
  type: string;
  submit: string;
  danger?: boolean;
  staff?: boolean;
  confirm?: string;
  hidden?: Record<string, string>;
  className?: string;
  children?: React.ReactNode;
}) {
  const [state, action, pending] = useActionState(staff ? runStaffAction : runAction, INITIAL);

  return (
    <form
      action={action}
      onSubmit={(e) => {
        if (confirm && !window.confirm(confirm)) e.preventDefault();
      }}
      className={className}
    >
      <input type="hidden" name="type" value={type} />
      {Object.entries(hidden).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <div className="flex flex-wrap items-end gap-2">
        {children}
        <button
          type="submit"
          disabled={pending}
          className={`${danger ? "btn-danger" : "btn-secondary"} px-4 py-2.5`}
        >
          {pending ? "Working…" : submit}
        </button>
      </div>
      {state.message && (
        <p role="status" className={`mt-2 text-sm ${state.ok ? "text-success" : "text-danger"}`}>
          {state.message}
        </p>
      )}
    </form>
  );
}
