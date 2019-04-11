module ${moduleName} {
    !{for requiredModule : requiresStatic}
    requires static ${requiredModule};
    !{/for}
    !{for requiredModule : requires}
    requires ${requiredModule};
    !{/for}
    !{for export : exports}
    exports ${export};
    !{/for}
}